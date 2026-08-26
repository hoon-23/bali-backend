package com.bali.batch.personalarchive

import com.bali.core.exercise.Exercise
import com.bali.core.exercise.ExerciseRepository
import com.bali.core.exercise.ExerciseScope
import com.bali.core.exercise.ExerciseType
import com.bali.core.exercise.MuscleGroup
import com.bali.core.session.SessionLog
import com.bali.core.session.SessionStatus
import com.bali.core.session.WorkoutSession
import com.bali.core.session.WorkoutSessionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.UUID

// 아이폰 메모 원본(4개 txt)에서 파싱한, 아직 종목 매핑 전인 raw 운동 기록 1건
data class RawEntry(
    val name: String,
    val muscleGroupHint: MuscleGroup?,
    val isCardio: Boolean = false,
    val weight: BigDecimal? = null,
    val reps: Int? = null,
    val sets: Int? = null,
    val durationSeconds: Int? = null,
    val pace: String? = null,
)

data class RawDay(val date: LocalDate, val entries: List<RawEntry>)

// 부위 한글 라벨 <-> MuscleGroup 매핑. 원본 파일 전반에서 부위 헤더/라벨로 쓰이는 단어들
private val BODY_PART_WORDS: Map<String, MuscleGroup> = mapOf(
    "등" to MuscleGroup.BACK,
    "가슴" to MuscleGroup.CHEST,
    "어깨" to MuscleGroup.SHOULDER,
    "이두" to MuscleGroup.BICEPS,
    "삼두" to MuscleGroup.TRICEPS,
    "하체" to MuscleGroup.LEGS,
    "복근" to MuscleGroup.ABS,
    "유산소" to MuscleGroup.CARDIO,
    "전신" to MuscleGroup.BACK,
    "코어" to MuscleGroup.ABS,
)

// 운동이 아닌 단어(부위 라벨과 별개로, 종목명으로 취급하면 안 되는 것들)
private val NON_EXERCISE_WORDS: Set<String> = setOf("스트레칭", "휴식")

// 오타/줄임말 -> 정식 GLOBAL 종목명. 실측 데이터에서 관찰된 것만 반영 (매핑 안 되면 PERSONAL로 즉석 생성)
private val ABBREVIATION_MAP: Map<String, String> = mapOf(
    "벤치" to "벤치프레스", "밴치" to "벤치프레스",
    "바벨로" to "바벨로우", "바벨로유" to "바벨로우",
    "스쾃" to "스쿼트",
    "데드" to "데드리프트",
    "밀프" to "밀리터리프레스", "밀리터리 프레스" to "밀리터리프레스",
    "bnp" to "비하인드넥프레스", "bhp" to "비하인드넥프레스",
    "숄프" to "숄더프레스", "숄더 프레스" to "숄더프레스",
    "팩덱" to "펙덱플라이", "펙덱" to "펙덱플라이", "펙댁" to "펙덱플라이",
    "리버스팩덱" to "펙덱플라이", "리버스 팩덱" to "펙덱플라이",
    "사레레" to "사이드레터럴레이즈",
    "레털럴 로우" to "레터럴로우", "레터럴 로우" to "레터럴로우",
    "렛풀" to "랫풀다운", "랫풀" to "랫풀다운",
    "클로즈드그립 랫풀" to "랫풀다운", "클로즈 그립 랫풀" to "랫풀다운", "클로즈그립 랫풀" to "랫풀다운",
    "시티드 로우" to "시티드로우", "시티드로" to "시티드로우", "시티드 로" to "시티드로우", "시티드 로 바로" to "시티드로우",
    "티바로" to "티바로우",
    "덤벨로" to "덤벨로우", "원암 덤벨로우" to "덤벨로우",
    "케이블 로우" to "케이블로우", "케이블로" to "케이블로우",
    "암풀" to "암풀다운",
    "머신 로우" to "머신로우", "머신로" to "머신로우", "머신 바벨로" to "머신로우",
    "체플" to "체스트플라이", "버터플" to "체스트플라이", "플라이" to "체스트플라이", "체스트 플라이" to "체스트플라이",
    "머신 벤치" to "머신체스트프레스", "머신벤치" to "머신체스트프레스",
    "푸시다운" to "케이블푸시다운", "푸쉬다운" to "케이블푸시다운", "푸시 다운" to "케이블푸시다운", "푸쉬 다운" to "케이블푸시다운",
    "스컬크러셔" to "시티드스컬크러셔", "스컬크러서" to "시티드스컬크러셔", "시티드 스컬 크러셔" to "시티드스컬크러셔",
    "케이블 익스" to "케이블익스텐션", "케이블익스" to "케이블익스텐션",
    "오버헤드" to "오버헤드익스텐션",
    "트라이 샙스" to "트라이셉스익스텐션",
    "불가리안" to "불가리안스플릿스쿼트",
    "핵스쾃" to "핵스쿼트",
    "리어델트" to "케이블리어델트플라이", "케이블 리어델트" to "케이블리어델트플라이",
    "레그 프레스" to "레그프레스",
    "레그 컬" to "레그컬",
    "바벨 컬" to "바벨컬", "덤벨 컬" to "덤벨컬", "케이블 컬" to "케이블컬",
    "ohp" to "OHP",
)

// 이름 매칭용 정규화: 공백 제거 + 대문자화(영문 약어 대소문자 무시, 한글엔 영향 없음)
private fun normalizeKey(s: String): String = s.replace(Regex("""\s+"""), "").uppercase()

// 괄호 안 잔여 설명, x2/*2 같은 횟수 접미사, 중복 공백을 제거해 종목명을 정리
private fun cleanExerciseName(raw: String): String =
    raw.replace(Regex("""\([^)]*\)"""), "")
        .replace(Regex("""[*×xX]\s*\d+$"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .removeSuffix("-")
        .trim()

private fun isSkippableWord(word: String): Boolean =
    word.isBlank() || word in BODY_PART_WORDS || word in NON_EXERCISE_WORDS

// 괄호 깊이를 추적하며 delimiter 기준으로 최상위 레벨에서만 분리 (중첩 괄호 안 콤마는 보존)
private fun splitTopLevel(s: String, delimiter: Char): List<String> {
    val parts = mutableListOf<String>()
    val sb = StringBuilder()
    var depth = 0
    for (c in s) {
        when {
            c == '(' -> { depth++; sb.append(c) }
            c == ')' -> { depth--; sb.append(c) }
            c == delimiter && depth == 0 -> { parts.add(sb.toString()); sb.clear() }
            else -> sb.append(c)
        }
    }
    if (sb.isNotEmpty()) parts.add(sb.toString())
    return parts
}

// "부위(종목,종목)" 및 괄호 없는 bare 목록 모두에서 종목명만 추출 (부위/비운동 단어 제외)
private fun extractExerciseNames(text: String): List<String> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return emptyList()
    val names = mutableListOf<String>()
    if (trimmed.contains("(")) {
        var i = 0
        while (i < trimmed.length) {
            if (trimmed[i] == '(') {
                var depth = 1
                var j = i + 1
                while (j < trimmed.length && depth > 0) {
                    when (trimmed[j]) { '(' -> depth++; ')' -> depth-- }
                    j++
                }
                val end = (if (depth == 0) j - 1 else trimmed.length).coerceAtLeast(i + 1)
                val inner = trimmed.substring(i + 1, end)
                names += splitTopLevel(inner, ',')
                i = end + 1
            } else {
                i++
            }
        }
    } else {
        names += trimmed.split(Regex("""[,\s]+"""))
    }
    return names.map { cleanExerciseName(it) }.filterNot { isSkippableWord(it) }
}

// "부위(종목,...)+부위(종목,...)" 라인에서 각 괄호 그룹 앞의 부위 라벨을 함께 추출
private fun extractGroupsWithHint(text: String): List<RawEntry> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return emptyList()
    if (!trimmed.contains("(")) return extractExerciseNames(trimmed).map { RawEntry(it, null) }

    val result = mutableListOf<RawEntry>()
    var i = 0
    while (i < trimmed.length) {
        if (trimmed[i] == '(') {
            var labelStart = i
            while (labelStart > 0 && trimmed[labelStart - 1] !in " \t+,") labelStart--
            val label = cleanExerciseName(trimmed.substring(labelStart, i))
            val hint = BODY_PART_WORDS[label]
            var depth = 1
            var j = i + 1
            while (j < trimmed.length && depth > 0) {
                when (trimmed[j]) { '(' -> depth++; ')' -> depth-- }
                j++
            }
            val end = (if (depth == 0) j - 1 else trimmed.length).coerceAtLeast(i + 1)
            val inner = trimmed.substring(i + 1, end)
            result += splitTopLevel(inner, ',').map { cleanExerciseName(it) }.filterNot { isSkippableWord(it) }.map { RawEntry(it, hint) }
            i = end + 1
        } else {
            i++
        }
    }
    return result
}

// 운동아카이브.txt 전용 파서. 종목명만 존재하고 무게/횟수는 절대 기록하지 않는다 (원본 형식이 그럼)
object ArchiveParser {
    private val yearHeader = Regex("""(\d{4})년\s*(\d{1,2})월""")
    private val dateLine = Regex("""^(\d{1,2})/(\d{1,2})\s*(.*)$""")
    private val bodyPartLine = Regex("""^-\s*\[.?]\s*(.+)$""")
    private val numberedLine = Regex("""^\d+\.\s*(.*)$""")

    fun parse(text: String): List<RawDay> {
        var currentYear = 2024
        var currentBodyPart: MuscleGroup? = null
        var activeDate: LocalDate? = null
        val days = LinkedHashMap<LocalDate, MutableList<RawEntry>>()

        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                currentBodyPart = null
                continue
            }

            val yh = yearHeader.find(line)
            if (yh != null) {
                currentYear = yh.groupValues[1].toInt()
                activeDate = null
                continue
            }

            val dl = dateLine.find(line)
            if (dl != null) {
                val month = dl.groupValues[1].toInt()
                val day = dl.groupValues[2].toInt()
                val date = runCatching { LocalDate.of(currentYear, month, day) }.getOrNull()
                if (date == null) {
                    activeDate = null
                    continue
                }
                activeDate = date
                currentBodyPart = null
                val bucket = days.getOrPut(date) { mutableListOf() }
                bucket += extractGroupsWithHint(dl.groupValues[3])
                continue
            }

            val date = activeDate ?: continue

            val bl = bodyPartLine.find(line)
            if (bl != null) {
                val word = cleanExerciseName(bl.groupValues[1])
                currentBodyPart = BODY_PART_WORDS[word]
                days.getOrPut(date) { mutableListOf() }
                continue
            }

            // "1. 종목명" 번호 목록 한 줄 = 종목 1개 (공백으로 추가 분리하지 않음, 콤마로 여러개면 분리)
            val nl = numberedLine.find(line)
            if (nl != null) {
                val content = nl.groupValues[1]
                val names = content.split(",").map { cleanExerciseName(it) }.filterNot { isSkippableWord(it) }
                if (names.isNotEmpty()) {
                    days.getOrPut(date) { mutableListOf() } += names.map { RawEntry(it, currentBodyPart) }
                }
                continue
            }

            // "- [x] 부위" 다음 줄에 콤마/공백으로 나열된 종목명 (부위 힌트 계승)
            val names = extractExerciseNames(line)
            if (names.isNotEmpty()) {
                days.getOrPut(date) { mutableListOf() } += names.map { RawEntry(it, currentBodyPart) }
            }
        }
        return days.map { (d, es) -> RawDay(d, es) }
    }
}

// 스트렝스.txt / 웨이트.txt / 웨이트2.txt 공용 파서 ("YY.MM.DD" + "- [x] 종목 무게 A*B" 형식)
object WeightStyleParser {
    private val dateLine = Regex("""^(\d{2})\.(\d{2})\.(\d{2})""")
    private val sectionLine = Regex("""^\*\s*(\S+)""")
    private val entryLine = Regex("""^-\s*\[x]\s*(.*)$""")
    private val minPattern = Regex("""(\d+(?:\.\d+)?)\s*min""", RegexOption.IGNORE_CASE)
    private val starPair = Regex("""(-?\d+(?:\.\d+)?)\s*\*\s*(\d+)""")
    private val anyNumber = Regex("""-?\d+(?:\.\d+)?""")

    fun parse(text: String): List<RawDay> {
        var activeDate: LocalDate? = null
        var currentSection: MuscleGroup? = null
        val days = LinkedHashMap<LocalDate, MutableList<RawEntry>>()

        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            val dl = dateLine.find(line)
            if (dl != null) {
                val year = 2000 + dl.groupValues[1].toInt()
                val month = dl.groupValues[2].toInt()
                val day = dl.groupValues[3].toInt()
                activeDate = runCatching { LocalDate.of(year, month, day) }.getOrNull()
                activeDate?.let { days.getOrPut(it) { mutableListOf() } }
                currentSection = null
                continue
            }

            val date = activeDate ?: continue

            val sl = sectionLine.find(line)
            if (sl != null) {
                currentSection = BODY_PART_WORDS[cleanExerciseName(sl.groupValues[1])]
                continue
            }

            val el = entryLine.find(line) ?: continue
            val content = el.groupValues[1].trim()
            if (content.isEmpty()) continue

            if (minPattern.containsMatchIn(content)) {
                val minutes = minPattern.find(content)!!.groupValues[1].toDouble()
                val leftover = anyNumber.replace(minPattern.replace(content, ""), "").trim()
                days.getOrPut(date) { mutableListOf() } += RawEntry(
                    name = "실내달리기",
                    muscleGroupHint = MuscleGroup.CARDIO,
                    isCardio = true,
                    durationSeconds = (minutes * 60).toInt(),
                    pace = leftover.ifBlank { null },
                )
                continue
            }

            val segments = content.split("/")
            val firstSegment = segments.first().trim()
            val lastSegment = segments.last().trim()

            val nameEndIdx = firstSegment.indexOfFirst { it.isDigit() }.let { if (it == -1) firstSegment.length else it }
            val name = cleanExerciseName(firstSegment.substring(0, nameEndIdx))
            if (name.isBlank()) continue

            var weight: BigDecimal? = null
            var reps: Int? = null
            var sets: Int? = null
            val pairMatch = starPair.find(lastSegment)
            if (pairMatch != null) {
                val before = lastSegment.substring(0, pairMatch.range.first)
                val after = lastSegment.substring(pairMatch.range.last + 1)
                val numsBefore = anyNumber.findAll(before).map { it.value.toBigDecimal() }.toList()
                val numsAfter = anyNumber.findAll(after).map { it.value.toBigDecimal() }.toList()
                val a = pairMatch.groupValues[1].toBigDecimal()
                val b = pairMatch.groupValues[2].toIntOrNull()
                when {
                    // "무게 A*B" (무게가 reps*sets 앞)
                    numsBefore.isNotEmpty() -> {
                        weight = numsBefore.last().abs()
                        reps = a.toInt()
                        sets = b
                    }
                    // "A*B 무게" (무게가 reps*sets 뒤, 웨이트3.txt류 신규 표기)
                    numsAfter.isNotEmpty() -> {
                        weight = numsAfter.first().abs()
                        reps = a.toInt()
                        sets = b
                    }
                    a.abs() > BigDecimal(20) -> {
                        weight = a.abs()
                        sets = b
                    }
                    else -> {
                        reps = a.toInt()
                        sets = b
                    }
                }
            } else {
                weight = anyNumber.find(lastSegment)?.value?.toBigDecimal()?.abs()
            }

            days.getOrPut(date) { mutableListOf() } += RawEntry(
                name = name, muscleGroupHint = currentSection, weight = weight, reps = reps, sets = sets,
            )
        }
        return days.map { (d, es) -> RawDay(d, es) }
    }
}

// GLOBAL/PERSONAL 종목 매칭. exact-name 기준(공백 무시, 영문 대소문자 무시)만 사용하고
// trigram suggest()는 UI 타이핑 자동완성용이라 여기서는 쓰지 않는다 (오매칭 위험)
class ExerciseResolver(private val exerciseRepository: ExerciseRepository, private val userId: UUID) {
    private val abbreviationMap: Map<String, String> = ABBREVIATION_MAP.mapKeys { normalizeKey(it.key) }
    private val visible: List<Exercise> = exerciseRepository.findVisibleTo(userId)
    private val index: MutableMap<String, Exercise> = visible
        .filter { it.variant == null }
        .associateBy { normalizeKey(it.name) }
        .toMutableMap()
    private val created = mutableListOf<Exercise>()

    // 실측 GLOBAL 시드엔 유산소 종목이 name='유산소'+variant='달리기'/'걷기'로만 존재해서
    // (variant=null만 보는 일반 resolve()로는 못 찾음) 별도 조회
    fun resolveCardio(): Exercise {
        visible.find { it.name == "유산소" && it.variant == "달리기" }?.let { return it }
        val saved = exerciseRepository.save(
            Exercise(id = null, name = "유산소", variant = "달리기", muscleGroup = MuscleGroup.CARDIO, type = ExerciseType.CARDIO, scope = ExerciseScope.PERSONAL, ownerId = userId)
        )
        created += saved
        return saved
    }

    fun resolve(rawName: String, type: ExerciseType, muscleGroup: MuscleGroup): Exercise {
        val key = normalizeKey(rawName)
        val canonicalName = abbreviationMap[key]
        if (canonicalName != null) {
            index[normalizeKey(canonicalName)]?.let { return it }
        }
        index[key]?.let { return it }

        val saved = exerciseRepository.save(
            Exercise(id = null, name = rawName, variant = null, muscleGroup = muscleGroup, type = type, scope = ExerciseScope.PERSONAL, ownerId = userId)
        )
        index[key] = saved
        created += saved
        return saved
    }

    fun createdExercises(): List<Exercise> = created
}

// 아이폰 메모에서 내보낸 개인 운동 아카이브(4개 txt)를 실 계정에 1회성으로 시딩하는 러너.
// Airflow와 무관하며 PersonalArchiveSeedMain에서 직접 실행한다. 정식 테스트 없이 실행 후
// DB 직접 조회로 대조 검증하는 방식으로 쓴다.
@Component
class PersonalArchiveSeedRunner(
    private val exerciseRepository: ExerciseRepository,
    private val sessionRepository: WorkoutSessionRepository,
) {
    private val log = LoggerFactory.getLogger(PersonalArchiveSeedRunner::class.java)

    fun run(userId: UUID, archiveDir: Path) {
        val sources = listOf(
            archiveDir.resolve("운동아카이브.txt") to ArchiveParser::parse,
            archiveDir.resolve("스트렝스.txt") to WeightStyleParser::parse,
            archiveDir.resolve("웨이트.txt") to WeightStyleParser::parse,
            archiveDir.resolve("웨이트2.txt") to WeightStyleParser::parse,
            archiveDir.resolve("웨이트3.txt") to WeightStyleParser::parse,
        )

        val existingDates = sessionRepository
            .findAllByUserId(userId, LocalDate.of(2020, 1, 1), LocalDate.of(2030, 12, 31))
            .map { it.date }
            .toSet()

        val resolver = ExerciseResolver(exerciseRepository, userId)
        var sessionCount = 0
        var logCount = 0
        var skippedExistingDates = 0

        sources.forEach { (path, parse) ->
            if (!Files.exists(path)) {
                log.warn("파일 없음, 스킵: $path")
                return@forEach
            }
            val rawDays = parse(Files.readString(path))
            rawDays.forEach { rawDay ->
                if (rawDay.date in existingDates) {
                    log.warn("이미 세션이 존재하는 날짜, 스킵: ${rawDay.date}")
                    skippedExistingDates++
                    return@forEach
                }

                val logs = rawDay.entries.mapIndexed { idx, entry ->
                    val exercise = if (entry.isCardio) {
                        resolver.resolveCardio()
                    } else {
                        resolver.resolve(entry.name, ExerciseType.STRENGTH, entry.muscleGroupHint ?: MuscleGroup.BACK)
                    }
                    val actualSets = if (exercise.type == ExerciseType.STRENGTH) entry.sets else null
                    val actualReps = if (exercise.type == ExerciseType.STRENGTH) entry.reps else null
                    val actualWeight = if (exercise.type == ExerciseType.STRENGTH) entry.weight else null
                    val actualDurationSeconds = if (exercise.type == ExerciseType.CARDIO) entry.durationSeconds else null
                    val actualPace = if (exercise.type == ExerciseType.CARDIO) entry.pace else null

                    SessionLog.validateActualFields(
                        exercise.type, actualSets, actualReps, actualWeight, actualDurationSeconds, actualPace,
                    )

                    SessionLog(
                        id = null,
                        exerciseId = exercise.id!!,
                        sortOrder = idx,
                        completed = true,
                        targetSets = null, targetReps = null, targetWeight = null,
                        targetDurationSeconds = null, targetPace = null,
                        actualSets = actualSets, actualReps = actualReps, actualWeight = actualWeight,
                        actualDurationSeconds = actualDurationSeconds, actualPace = actualPace,
                    )
                }

                sessionRepository.save(
                    WorkoutSession(id = null, userId = userId, date = rawDay.date, templateId = null, status = SessionStatus.COMPLETED, logs = logs)
                )
                sessionCount++
                logCount += logs.size
            }
        }

        log.info("시딩 완료: session=$sessionCount, log=$logCount, 기존날짜스킵=$skippedExistingDates, PERSONAL종목생성=${resolver.createdExercises().size}")
        resolver.createdExercises().forEach {
            log.info("PERSONAL 종목 생성: name=${it.name}, muscleGroup=${it.muscleGroup}, type=${it.type}")
        }
    }
}
