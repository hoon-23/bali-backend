package com.bali.core.analysis

import com.bali.core.exercise.Equipment
import com.bali.core.exercise.Exercise
import com.bali.core.exercise.ExerciseScope
import com.bali.core.exercise.ExerciseType
import com.bali.core.exercise.MuscleGroup
import com.bali.core.session.SessionLog
import com.bali.core.session.SessionStatus
import com.bali.core.session.WorkoutRecordPolicy
import com.bali.core.session.WorkoutSession
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class WeeklyStatsCalculatorTest : StringSpec({

    val benchPressId = UUID.randomUUID()
    val squatId = UUID.randomUUID()
    val runningId = UUID.randomUUID()
    val burpeeId = UUID.randomUUID()

    fun exercise(id: UUID, type: ExerciseType, muscleGroup: MuscleGroup, equipment: Equipment? = null) = Exercise(
        id = id, name = "test", variant = null, muscleGroup = muscleGroup, type = type, scope = ExerciseScope.GLOBAL, ownerId = null,
        equipment = equipment,
    )

    val exercisesById = mapOf(
        benchPressId to exercise(benchPressId, ExerciseType.STRENGTH, MuscleGroup.CHEST),
        squatId to exercise(squatId, ExerciseType.STRENGTH, MuscleGroup.LEGS),
        runningId to exercise(runningId, ExerciseType.CARDIO, MuscleGroup.CARDIO),
        burpeeId to exercise(burpeeId, ExerciseType.STRENGTH, MuscleGroup.FUNCTIONAL, Equipment.BODYWEIGHT),
    )

    fun strengthLog(exerciseId: UUID, completed: Boolean, sets: Int = 3, reps: Int = 10, weight: String = "60.0") =
        SessionLog.create(ExerciseType.STRENGTH, exerciseId, sortOrder = 0, targetSets = sets, targetReps = reps, targetWeight = BigDecimal(weight))
            .copy(completed = completed, actualSets = if (completed) sets else null, actualReps = if (completed) reps else null, actualWeight = if (completed) BigDecimal(weight) else null)

    fun cardioLog(exerciseId: UUID, completed: Boolean, durationSeconds: Int = 1800) =
        SessionLog.create(ExerciseType.CARDIO, exerciseId, sortOrder = 0, targetDurationSeconds = durationSeconds)
            .copy(completed = completed, actualDurationSeconds = if (completed) durationSeconds else null)

    // 로그 묶음 하나를 세션 하나로 감싼다
    fun session(vararg logs: SessionLog, status: SessionStatus = SessionStatus.COMPLETED) = WorkoutSession(
        id = null, userId = UUID.randomUUID(), date = LocalDate.of(2026, 9, 1), templateId = null, status = status, logs = logs.toList(),
    )

    // 로그 전체를 세션 하나에 담아 계산 (세션 단위 집계와 무관한 기존 검증용)
    fun calculateOneSession(logs: List<SessionLog>, previousSummary: AnalysisSummary? = null) =
        WeeklyStatsCalculator.calculate(listOf(session(*logs.toTypedArray())), exercisesById, previousSummary)

    // 직전 기간 요약: 볼륨 총합/볼륨 세션 수/맨몸 반복수 총합/맨몸 세션 수만 채운다
    fun previousSummary(volume: String? = null, weightedSessions: Int? = null, bodyweightReps: Int? = null, bodyweightSessions: Int? = null) = AnalysisSummary(
        0, if (volume == null) emptyMap() else mapOf(benchPressId to BigDecimal(volume)), emptyMap(), 0, BigDecimal.ZERO, null,
        bodyweightRepsByExercise = if (bodyweightReps == null) emptyMap() else mapOf(burpeeId to bodyweightReps),
        weightedSessionCount = weightedSessions, bodyweightSessionCount = bodyweightSessions,
    )

    "완료된 STRENGTH 로그의 볼륨은 sets*reps*weight로 종목별/근육군별 합산된다" {
        val logs = listOf(strengthLog(benchPressId, completed = true, sets = 3, reps = 10, weight = "60.0"))

        val summary = calculateOneSession(logs)

        summary.volumeByExercise[benchPressId] shouldBe BigDecimal("1800.0")
        summary.volumeByMuscleGroup[MuscleGroup.CHEST] shouldBe BigDecimal("1800.0")
    }

    "완료되지 않은 로그는 볼륨/유산소 시간 계산에서 제외된다" {
        val logs = listOf(strengthLog(benchPressId, completed = false), cardioLog(runningId, completed = false))

        val summary = calculateOneSession(logs)

        summary.volumeByExercise.isEmpty() shouldBe true
        summary.cardioTotalMinutes shouldBe 0
    }

    "완료된 CARDIO 로그의 duration은 분 단위로 합산된다" {
        val logs = listOf(cardioLog(runningId, completed = true, durationSeconds = 1800))

        val summary = calculateOneSession(logs)

        summary.cardioTotalMinutes shouldBe 30
    }

    "totalWorkoutMinutes는 완료된 STRENGTH 로그 수*12 + cardioTotalMinutes다" {
        val logs = listOf(
            strengthLog(benchPressId, completed = true), strengthLog(squatId, completed = true),
            cardioLog(runningId, completed = true, durationSeconds = 600),
        )

        val summary = calculateOneSession(logs)

        summary.totalWorkoutMinutes shouldBe (2 * 12 + 10)
    }

    "completionRate는 완료 로그 수/전체 로그 수 * 100이다" {
        val logs = listOf(strengthLog(benchPressId, completed = true), strengthLog(squatId, completed = false))

        val summary = calculateOneSession(logs)

        summary.completionRate shouldBe BigDecimal("50.0")
    }

    "로그가 하나도 없으면 completionRate는 0이다" {
        val summary = WeeklyStatsCalculator.calculate(emptyList(), exercisesById, previousSummary = null)

        summary.completionRate shouldBe BigDecimal.ZERO
    }

    "지난주 요약이 없으면 volumeChangeFromLastWeekPercent는 null이다" {
        calculateOneSession(listOf(strengthLog(benchPressId, completed = true))).volumeChangeFromLastWeekPercent shouldBe null
    }

    "세션당 볼륨 증감률은 총합이 아니라 세션당 평균끼리 비교한다" {
        // 이번 주: 세션 3개, 각 1800 (평균 1800). 지난주: 세션 2개, 총 3000 (평균 1500) → +20%
        val sessions = List(3) { session(strengthLog(benchPressId, completed = true, sets = 3, reps = 10, weight = "60.0")) }

        val summary = WeeklyStatsCalculator.calculate(sessions, exercisesById, previousSummary(volume = "3000.0", weightedSessions = 2))

        summary.volumeChangeFromLastWeekPercent shouldBe BigDecimal("20.0")
        summary.weightedSessionCount shouldBe 3
    }

    "같은 강도로 횟수만 늘면 세션당 볼륨 증감률은 0이다" {
        // 지난주 2회(총 3600) → 이번 주 4회(총 7200). 총합은 2배지만 세션당 평균은 그대로
        val sessions = List(4) { session(strengthLog(benchPressId, completed = true, sets = 3, reps = 10, weight = "60.0")) }

        val summary = WeeklyStatsCalculator.calculate(sessions, exercisesById, previousSummary(volume = "3600.0", weightedSessions = 2))

        summary.volumeChangeFromLastWeekPercent!!.compareTo(BigDecimal.ZERO) shouldBe 0
    }

    "지난주 볼륨 세션이 2회 미만이거나 세션 수가 없는 구버전 요약이면 증감률은 null이다" {
        val sessions = listOf(session(strengthLog(benchPressId, completed = true)))

        WeeklyStatsCalculator.calculate(sessions, exercisesById, previousSummary(volume = "1500.0", weightedSessions = 1)).volumeChangeFromLastWeekPercent shouldBe null
        WeeklyStatsCalculator.calculate(sessions, exercisesById, previousSummary(volume = "1500.0", weightedSessions = null)).volumeChangeFromLastWeekPercent shouldBe null
    }

    "이번 주에 볼륨이 있는 세션이 없으면 증감률은 null이다" {
        val sessions = listOf(session(cardioLog(runningId, completed = true)))

        WeeklyStatsCalculator.calculate(sessions, exercisesById, previousSummary(volume = "3000.0", weightedSessions = 2)).volumeChangeFromLastWeekPercent shouldBe null
    }

    "유산소나 맨몸만 한 세션은 세션당 볼륨의 분모에서 빠진다" {
        val sessions = listOf(
            session(strengthLog(benchPressId, completed = true, sets = 3, reps = 10, weight = "60.0")),
            session(cardioLog(runningId, completed = true)),
            session(strengthLog(burpeeId, completed = true, sets = 3, reps = 15, weight = "0")),
        )

        val summary = WeeklyStatsCalculator.calculate(sessions, exercisesById, previousSummary(volume = "3600.0", weightedSessions = 2))

        summary.sessionCount shouldBe 3
        summary.weightedSessionCount shouldBe 1
        summary.bodyweightSessionCount shouldBe 1
        summary.volumeChangeFromLastWeekPercent!!.compareTo(BigDecimal.ZERO) shouldBe 0
    }

    "운동 세션 수는 상태와 무관하게 완료 로그가 있는 세션만 센다" {
        val sessions = listOf(
            session(strengthLog(benchPressId, completed = true)),
            session(strengthLog(benchPressId, completed = true), status = SessionStatus.ABANDONED),
            session(strengthLog(benchPressId, completed = false), status = SessionStatus.SCHEDULED),
        )

        val summary = WeeklyStatsCalculator.calculate(sessions, exercisesById, previousSummary = null, previousSessionCount = 3)

        summary.sessionCount shouldBe 2
        summary.previousSessionCount shouldBe 3
    }

    "직전 운동 세션 수는 분석 상태로 정한다" {
        val withCount = AnalysisSummary(0, emptyMap(), emptyMap(), 0, BigDecimal.ZERO, null, sessionCount = 3)
        val legacy = AnalysisSummary(0, emptyMap(), emptyMap(), 0, BigDecimal.ZERO, null)

        PeriodStatsCalculator.previousSessionCount(AnalysisStatus.SUCCESS, withCount) shouldBe 3
        PeriodStatsCalculator.previousSessionCount(AnalysisStatus.SUCCESS, legacy) shouldBe null
        PeriodStatsCalculator.previousSessionCount(AnalysisStatus.NO_ACTIVITY, null) shouldBe 0
        PeriodStatsCalculator.previousSessionCount(AnalysisStatus.FAILED, null) shouldBe null
        PeriodStatsCalculator.previousSessionCount(null, null) shouldBe null
    }

    // 빈도 문장 검증용 요약: 이번/직전 운동 세션 수만 채운다
    fun frequencySummary(current: Int?, previous: Int?) =
        AnalysisSummary(60, emptyMap(), emptyMap(), 0, BigDecimal("100.0"), null, sessionCount = current, previousSessionCount = previous)

    "운동 횟수가 달라지면 횟수 비교 문장이 생성된다" {
        WeeklyStatsCalculator.generateInsights(frequencySummary(4, 3)).map { it.summaryText } shouldBe listOf("지난주 3회 → 이번 주 4회")
        WeeklyStatsCalculator.generateInsights(frequencySummary(3, 0)).map { it.summaryText } shouldBe listOf("지난주 0회 → 이번 주 3회")
    }

    "운동 횟수가 같고 2회 이상이면 꾸준해요 문장이 생성되고 1회면 생성되지 않는다" {
        WeeklyStatsCalculator.generateInsights(frequencySummary(3, 3)).map { it.summaryText } shouldBe listOf("이번 주도 3회, 꾸준해요")
        WeeklyStatsCalculator.generateInsights(frequencySummary(1, 1)).isEmpty() shouldBe true
    }

    "이번 또는 직전 운동 횟수를 모르면 빈도 문장이 생성되지 않는다" {
        WeeklyStatsCalculator.generateInsights(frequencySummary(3, null)).isEmpty() shouldBe true
        WeeklyStatsCalculator.generateInsights(frequencySummary(null, 3)).isEmpty() shouldBe true
    }

    "증가율이 정확히 100%면 퍼센트로 쓰고 100%를 넘으면 크게 늘었어요로 쓴다" {
        val exactly = AnalysisSummary(60, emptyMap(), emptyMap(), 0, BigDecimal("100.0"), BigDecimal("100.0"))
        val over = AnalysisSummary(60, emptyMap(), emptyMap(), 0, BigDecimal("100.0"), BigDecimal("100.1"), bodyweightRepsChangeFromLastWeekPercent = BigDecimal("331.1"))

        WeeklyStatsCalculator.generateInsights(exactly).map { it.summaryText } shouldBe listOf("이번 주 세션당 볼륨이 지난주보다 100.0% 증가했어요")
        WeeklyStatsCalculator.generateInsights(over).map { it.summaryText } shouldBe listOf(
            "이번 주 세션당 볼륨이 지난주보다 크게 늘었어요",
            "이번 주 세션당 맨몸 운동 반복수가 지난주보다 크게 늘었어요",
        )
    }

    "인사이트는 빈도, 세션당 볼륨, 완료율, 근육군 비중, 세션당 맨몸 반복수 순서다" {
        val summary = AnalysisSummary(
            60, emptyMap(), emptyMap(), 0, BigDecimal("50.0"), BigDecimal("20.0"),
            setsByMuscleGroup = mapOf(MuscleGroup.CHEST to 9, MuscleGroup.LEGS to 1),
            bodyweightRepsChangeFromLastWeekPercent = BigDecimal("-20.0"),
            sessionCount = 4, previousSessionCount = 3,
        )

        WeeklyStatsCalculator.generateInsights(summary).map { it.summaryText } shouldBe listOf(
            "지난주 3회 → 이번 주 4회",
            "이번 주 세션당 볼륨이 지난주보다 20.0% 증가했어요",
            "완료율이 50.0%로 낮은 편이에요",
            "하체 비중이 10.0%로 낮은 편이에요",
            "이번 주 세션당 맨몸 운동 반복수가 지난주보다 20.0% 감소했어요",
        )
    }

    "볼륨이 지난주보다 10% 이상 늘면 증가 인사이트가 생성된다" {
        val summary = AnalysisSummary(60, emptyMap(), emptyMap(), 0, BigDecimal("100.0"), BigDecimal("20.0"))

        val insights = WeeklyStatsCalculator.generateInsights(summary)

        insights.map { it.summaryText } shouldBe listOf("이번 주 세션당 볼륨이 지난주보다 20.0% 증가했어요")
    }

    "볼륨이 지난주보다 10% 이상 줄면 감소 인사이트가 생성된다" {
        val summary = AnalysisSummary(60, emptyMap(), emptyMap(), 0, BigDecimal("100.0"), BigDecimal("-15.0"))

        val insights = WeeklyStatsCalculator.generateInsights(summary)

        insights.map { it.summaryText } shouldBe listOf("이번 주 세션당 볼륨이 지난주보다 15.0% 감소했어요")
    }

    "볼륨 증감이 10% 미만이면 증감 인사이트가 생성되지 않는다" {
        val summary = AnalysisSummary(60, emptyMap(), emptyMap(), 0, BigDecimal("100.0"), BigDecimal("5.0"))

        val insights = WeeklyStatsCalculator.generateInsights(summary)

        insights.isEmpty() shouldBe true
    }

    "completionRate가 70% 미만이면 완료율 인사이트가 생성된다" {
        val summary = AnalysisSummary(60, emptyMap(), emptyMap(), 0, BigDecimal("50.0"), null)

        val insights = WeeklyStatsCalculator.generateInsights(summary)

        insights.map { it.summaryText } shouldBe listOf("완료율이 50.0%로 낮은 편이에요")
    }

    "근육군 중 하나의 세트 비중이 15% 미만이면 불균형 인사이트가 생성된다" {
        val summary = AnalysisSummary(
            60, emptyMap(), emptyMap(), 0, BigDecimal("100.0"), null,
            setsByMuscleGroup = mapOf(MuscleGroup.CHEST to 9, MuscleGroup.LEGS to 1),
        )

        val insights = WeeklyStatsCalculator.generateInsights(summary)

        insights.map { it.summaryText } shouldBe listOf("하체 비중이 10.0%로 낮은 편이에요")
    }

    "근육군이 하나뿐이면 불균형 인사이트가 생성되지 않는다" {
        val summary = AnalysisSummary(
            60, emptyMap(), emptyMap(), 0, BigDecimal("100.0"), null,
            setsByMuscleGroup = mapOf(MuscleGroup.CHEST to 9),
        )

        val insights = WeeklyStatsCalculator.generateInsights(summary)

        insights.isEmpty() shouldBe true
    }

    "아무 규칙도 해당하지 않으면 insights는 빈 리스트다" {
        val summary = AnalysisSummary(
            60, emptyMap(), emptyMap(), 0, BigDecimal("100.0"), BigDecimal("2.0"),
            setsByMuscleGroup = mapOf(MuscleGroup.CHEST to 5, MuscleGroup.LEGS to 5),
        )

        val insights = WeeklyStatsCalculator.generateInsights(summary)

        insights.isEmpty() shouldBe true
    }

    "무게 볼륨이 0인 맨몸 근육군도 세트 비중이 충분하면 불균형 인사이트가 생성되지 않는다" {
        val summary = AnalysisSummary(
            60, emptyMap(),
            mapOf(MuscleGroup.CHEST to BigDecimal("900.0"), MuscleGroup.FUNCTIONAL to BigDecimal.ZERO),
            0, BigDecimal("100.0"), null,
            setsByMuscleGroup = mapOf(MuscleGroup.CHEST to 5, MuscleGroup.FUNCTIONAL to 5),
        )

        val insights = WeeklyStatsCalculator.generateInsights(summary)

        insights.isEmpty() shouldBe true
    }

    "맨몸 종목 로그는 bodyweightRepsByExercise에 sets*reps로 집계되고 무게 볼륨은 0이다" {
        val logs = listOf(strengthLog(burpeeId, completed = true, sets = 3, reps = 15, weight = "0"))

        val summary = calculateOneSession(logs)

        summary.bodyweightRepsByExercise shouldBe mapOf(burpeeId to 45)
        summary.volumeByExercise.getValue(burpeeId).compareTo(BigDecimal.ZERO) shouldBe 0
    }

    "중량 종목은 bodyweightRepsByExercise에 포함되지 않는다" {
        val logs = listOf(strengthLog(benchPressId, completed = true))

        val summary = calculateOneSession(logs)

        summary.bodyweightRepsByExercise.isEmpty() shouldBe true
    }

    "완료되지 않았거나 reps가 비어 있는 맨몸 로그는 bodyweightRepsByExercise에서 제외된다" {
        val incomplete = strengthLog(burpeeId, completed = false, weight = "0")
        val noReps = strengthLog(burpeeId, completed = true, weight = "0").copy(actualReps = null)

        val summary = calculateOneSession(listOf(incomplete, noReps))

        summary.bodyweightRepsByExercise.isEmpty() shouldBe true
    }

    "setsByMuscleGroup은 중량/맨몸 구분 없이 완료 세트를 근육군별로 합산한다" {
        val logs = listOf(
            strengthLog(benchPressId, completed = true, sets = 3),
            strengthLog(burpeeId, completed = true, sets = 4, weight = "0"),
            strengthLog(squatId, completed = false, sets = 5),
        )

        val summary = calculateOneSession(logs)

        summary.setsByMuscleGroup shouldBe mapOf(MuscleGroup.CHEST to 3, MuscleGroup.FUNCTIONAL to 4)
    }

    "세션당 맨몸 반복수 증감률은 맨몸 기록이 있는 세션의 평균끼리 비교한다" {
        // 이번 주: 세션 1개, 45회 (평균 45). 지난주: 세션 2개, 총 60회 (평균 30) → +50%
        val sessions = listOf(session(strengthLog(burpeeId, completed = true, sets = 3, reps = 15, weight = "0")))

        val summary = WeeklyStatsCalculator.calculate(sessions, exercisesById, previousSummary(bodyweightReps = 60, bodyweightSessions = 2))

        summary.bodyweightRepsChangeFromLastWeekPercent shouldBe BigDecimal("50.0")
    }

    "지난주 맨몸 세션이 2회 미만이거나 세션 수가 없으면 맨몸 증감률은 null이다" {
        val sessions = listOf(session(strengthLog(burpeeId, completed = true, weight = "0")))

        WeeklyStatsCalculator.calculate(sessions, exercisesById, previousSummary(bodyweightReps = 30, bodyweightSessions = 1)).bodyweightRepsChangeFromLastWeekPercent shouldBe null
        WeeklyStatsCalculator.calculate(sessions, exercisesById, previousSummary(bodyweightReps = 30)).bodyweightRepsChangeFromLastWeekPercent shouldBe null
        WeeklyStatsCalculator.calculate(sessions, exercisesById, previousSummary()).bodyweightRepsChangeFromLastWeekPercent shouldBe null
    }

    "맨몸 반복수가 지난주보다 10% 이상 늘면 증가 인사이트가 생성된다" {
        val summary = AnalysisSummary(60, emptyMap(), emptyMap(), 0, BigDecimal("100.0"), null, bodyweightRepsChangeFromLastWeekPercent = BigDecimal("50.0"))

        val insights = WeeklyStatsCalculator.generateInsights(summary)

        insights.map { it.summaryText } shouldBe listOf("이번 주 세션당 맨몸 운동 반복수가 지난주보다 50.0% 증가했어요")
    }

    "맨몸 반복수가 지난주보다 10% 이상 줄면 감소 인사이트가 생성된다" {
        val summary = AnalysisSummary(60, emptyMap(), emptyMap(), 0, BigDecimal("100.0"), null, bodyweightRepsChangeFromLastWeekPercent = BigDecimal("-20.0"))

        val insights = WeeklyStatsCalculator.generateInsights(summary)

        insights.map { it.summaryText } shouldBe listOf("이번 주 세션당 맨몸 운동 반복수가 지난주보다 20.0% 감소했어요")
    }

    "맨몸 반복수 증감이 10% 미만이면 인사이트가 생성되지 않는다" {
        val summary = AnalysisSummary(60, emptyMap(), emptyMap(), 0, BigDecimal("100.0"), null, bodyweightRepsChangeFromLastWeekPercent = BigDecimal("5.0"))

        WeeklyStatsCalculator.generateInsights(summary).isEmpty() shouldBe true
    }

    "중단 세션의 로그는 완료율에서 제외되지만 완료한 로그의 볼륨은 그대로 포함된다" {
        fun withId(log: SessionLog) = log.copy(id = UUID.randomUUID())
        val normal = session(withId(strengthLog(benchPressId, completed = true)), withId(strengthLog(squatId, completed = false)))
        val abandonedLogs = arrayOf(
            withId(strengthLog(squatId, completed = false)), withId(strengthLog(squatId, completed = false)),
            withId(strengthLog(benchPressId, completed = true)),
        )

        val withoutExclusion = WeeklyStatsCalculator.calculate(listOf(normal, session(*abandonedLogs)), exercisesById, previousSummary = null)
        val summary = WeeklyStatsCalculator.calculate(
            listOf(normal, session(*abandonedLogs, status = SessionStatus.ABANDONED)), exercisesById, previousSummary = null,
        )

        withoutExclusion.completionRate shouldBe BigDecimal("40.0")
        summary.completionRate shouldBe BigDecimal("50.0")
        summary.volumeByExercise[benchPressId] shouldBe BigDecimal("3600.0")
    }

    "모든 로그가 중단 세션 소속이면 완료율은 0이다" {
        val log = strengthLog(benchPressId, completed = true).copy(id = UUID.randomUUID())

        val summary = WeeklyStatsCalculator.calculate(listOf(session(log, status = SessionStatus.ABANDONED)), exercisesById, previousSummary = null)

        summary.completionRate shouldBe BigDecimal.ZERO
    }

    "기준일 이후 세션에서 기록 없이 완료 체크만 한 로그는 시간과 세트와 세션 수에서 빠지고 완료율을 낮춘다" {
        fun strictSession(vararg logs: SessionLog) = session(*logs).copy(date = WorkoutRecordPolicy.STRICT_FROM)
        val checkedOnly = strengthLog(squatId, completed = true).copy(actualSets = 0, actualReps = 0)
        val sessions = listOf(strictSession(strengthLog(benchPressId, completed = true, sets = 3)), strictSession(checkedOnly))

        val summary = WeeklyStatsCalculator.calculate(sessions, exercisesById, previousSummary = null)

        summary.sessionCount shouldBe 1
        summary.totalWorkoutMinutes shouldBe 12
        summary.setsByMuscleGroup shouldBe mapOf(MuscleGroup.CHEST to 3)
        summary.volumeByExercise.keys shouldBe setOf(benchPressId)
        summary.completionRate shouldBe BigDecimal("50.0")
    }

    "기준일 이전 세션은 기록 없이 완료 체크만 해도 그대로 집계된다" {
        val checkedOnly = strengthLog(squatId, completed = true).copy(actualSets = null, actualReps = null)
        val sessions = listOf(session(checkedOnly).copy(date = WorkoutRecordPolicy.STRICT_FROM.minusDays(1)))

        val summary = WeeklyStatsCalculator.calculate(sessions, exercisesById, previousSummary = null)

        summary.sessionCount shouldBe 1
        summary.totalWorkoutMinutes shouldBe 12
        summary.completionRate shouldBe BigDecimal("100.0")
    }
})
