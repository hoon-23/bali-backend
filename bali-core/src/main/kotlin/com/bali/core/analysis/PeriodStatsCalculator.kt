package com.bali.core.analysis

import com.bali.core.exercise.Equipment
import com.bali.core.exercise.Exercise
import com.bali.core.exercise.ExerciseType
import com.bali.core.session.SessionLog
import com.bali.core.session.WorkoutSession
import com.bali.core.session.abandonedLogIds
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

// 기간(주/월) 라벨과 "꾸준해요" 문장을 만드는 최소 운동 횟수
data class PeriodLabels(val current: String, val previous: String, val steadyMinSessions: Int)

// 한 기간의 세션 목록 + 종목 메타데이터로부터 집계값/규칙 기반 인사이트를 계산하는 주간/월간 공용 순수 함수 모음.
// 빈도(운동 세션 수)는 횟수로, 강도(볼륨/맨몸 반복수)는 세션당 평균으로 직전 기간과 비교한다
object PeriodStatsCalculator {

    // 완료된 STRENGTH 로그 1개당 추정 소요 시간(분). 사용자 실측 기록(휴식 제외 종목당 10~15분) 기반 중간값
    private const val MINUTES_PER_STRENGTH_EXERCISE = 12
    private val CHANGE_THRESHOLD = BigDecimal(10)
    private val LOW_COMPLETION_THRESHOLD = BigDecimal(70)

    // 증가율이 이 값을 넘으면 숫자 대신 "크게 늘었어요"로 쓴다 (세션당 평균으로도 드물게 튀는 경우의 안전망)
    private val LARGE_INCREASE_THRESHOLD = BigDecimal(100)

    // 직전 기간의 해당 세션이 이보다 적으면 평균이 기록 한 번에 좌우되므로 강도 증감을 계산하지 않는다
    private const val MIN_PREVIOUS_SESSIONS = 2

    // 기간 내 세션 목록으로부터 AnalysisSummary를 계산. exercisesById는 세션 로그에 등장하는 모든 exerciseId를 커버해야 함.
    // previousSessionCount는 직전 기간의 운동 세션 수(모르면 null)
    fun calculate(
        sessions: List<WorkoutSession>,
        exercisesById: Map<UUID, Exercise>,
        previousSummary: AnalysisSummary?,
        previousSessionCount: Int? = null,
    ): AnalysisSummary {
        val logs = sessions.flatMap { it.logs }
        val completedLogs = logs.filter { it.completed }
        val strengthLogs = completedLogs.filter { exercisesById.getValue(it.exerciseId).type == ExerciseType.STRENGTH }
        val cardioLogs = completedLogs.filter { exercisesById.getValue(it.exerciseId).type == ExerciseType.CARDIO }

        val volumeByExercise = strengthLogs
            .groupBy { it.exerciseId }
            .mapValues { (_, exerciseLogs) -> exerciseLogs.fold(BigDecimal.ZERO) { acc, log -> acc + volumeOf(log) } }

        val volumeByMuscleGroup = strengthLogs
            .groupBy { exercisesById.getValue(it.exerciseId).muscleGroup }
            .mapValues { (_, groupLogs) -> groupLogs.fold(BigDecimal.ZERO) { acc, log -> acc + volumeOf(log) } }

        val cardioTotalMinutes = cardioLogs.sumOf { it.actualDurationSeconds ?: 0 } / 60
        val totalWorkoutMinutes = strengthLogs.size * MINUTES_PER_STRENGTH_EXERCISE + cardioTotalMinutes

        // 중단(ABANDONED) 세션의 로그는 완료율의 분자/분모 모두에서 제외한다 (볼륨 등은 completedLogs로 그대로 계산)
        val abandonedLogIds = sessions.abandonedLogIds()
        val ratedLogs = logs.filterNot { it.id != null && it.id in abandonedLogIds }
        val completionRate = if (ratedLogs.isEmpty()) {
            BigDecimal.ZERO
        } else {
            BigDecimal(ratedLogs.count { it.completed }).multiply(BigDecimal(100))
                .divide(BigDecimal(ratedLogs.size), 1, RoundingMode.HALF_UP)
        }

        // 세션 단위 집계: 상태와 무관하게 완료 로그가 있으면 운동한 세션으로 센다 (볼륨 집계와 같은 기준)
        val sessionCount = sessions.count { session -> session.logs.any { it.completed } }
        val weightedSessionCount = sessions.count { sessionVolume(it, exercisesById) > BigDecimal.ZERO }
        val bodyweightSessionCount = sessions.count { sessionBodyweightReps(it, exercisesById) > 0 }

        val bodyweightRepsByExercise = BodyweightStats.repsByExercise(strengthLogs, exercisesById)

        return AnalysisSummary(
            totalWorkoutMinutes = totalWorkoutMinutes,
            volumeByExercise = volumeByExercise,
            volumeByMuscleGroup = volumeByMuscleGroup,
            cardioTotalMinutes = cardioTotalMinutes,
            completionRate = completionRate,
            volumeChangeFromLastWeekPercent = previousSummary?.let { prev ->
                perSessionChangePercent(
                    currentTotal = volumeByExercise.values.fold(BigDecimal.ZERO, BigDecimal::add), currentSessions = weightedSessionCount,
                    previousTotal = prev.volumeByExercise.values.fold(BigDecimal.ZERO, BigDecimal::add), previousSessions = prev.weightedSessionCount,
                )
            },
            bodyweightRepsByExercise = bodyweightRepsByExercise,
            setsByMuscleGroup = BodyweightStats.setsByMuscleGroup(strengthLogs, exercisesById),
            bodyweightRepsChangeFromLastWeekPercent = previousSummary?.let { prev ->
                perSessionChangePercent(
                    currentTotal = BigDecimal(bodyweightRepsByExercise.values.sum()), currentSessions = bodyweightSessionCount,
                    previousTotal = BigDecimal(prev.bodyweightRepsByExercise.values.sum()), previousSessions = prev.bodyweightSessionCount,
                )
            },
            sessionCount = sessionCount,
            previousSessionCount = previousSessionCount,
            weightedSessionCount = weightedSessionCount,
            bodyweightSessionCount = bodyweightSessionCount,
        )
    }

    // 직전 기간 분석으로부터 직전 운동 세션 수를 정한다. 분석이 없거나 실패했거나 구버전 요약(세션 수 없음)이면 null, 활동 없음이면 0
    fun previousSessionCount(previousStatus: AnalysisStatus?, previousSummary: AnalysisSummary?): Int? = when (previousStatus) {
        AnalysisStatus.NO_ACTIVITY -> 0
        AnalysisStatus.SUCCESS -> previousSummary?.sessionCount
        AnalysisStatus.FAILED, null -> null
    }

    // AnalysisSummary로부터 규칙 기반 인사이트 문장을 생성 (조건 미충족 규칙은 문장을 만들지 않음). 순서: 빈도 → 세션당 볼륨 → 완료율 → 근육군 비중 → 세션당 맨몸 반복수
    fun generateInsights(summary: AnalysisSummary, labels: PeriodLabels): List<Insight> {
        val insights = mutableListOf<Insight>()

        frequencyInsight(summary.sessionCount, summary.previousSessionCount, labels)?.let { insights += it }
        changeInsight(summary.volumeChangeFromLastWeekPercent, "세션당 볼륨이", labels)?.let { insights += it }

        if (summary.completionRate < LOW_COMPLETION_THRESHOLD) {
            insights += Insight(id = null, summaryText = "완료율이 ${summary.completionRate}%로 낮은 편이에요")
        }

        BodyweightStats.muscleShareInsight(summary.setsByMuscleGroup)?.let { insights += it }
        changeInsight(summary.bodyweightRepsChangeFromLastWeekPercent, "세션당 맨몸 운동 반복수가", labels)?.let { insights += it }

        return insights
    }

    // 세션당 평균의 직전 기간 대비 증감률(%). 직전 세션 수를 모르거나(구버전 요약) 2회 미만이거나, 이번 기간 세션이 없으면 null
    private fun perSessionChangePercent(
        currentTotal: BigDecimal, currentSessions: Int,
        previousTotal: BigDecimal, previousSessions: Int?,
    ): BigDecimal? {
        if (previousSessions == null || previousSessions < MIN_PREVIOUS_SESSIONS || currentSessions == 0) return null
        // (이번 평균 - 직전 평균) / 직전 평균을 통분한 식. 세션 수가 0보다 크면 총합도 0보다 크므로 분모는 0이 아니다
        val numerator = currentTotal.multiply(BigDecimal(previousSessions)).subtract(previousTotal.multiply(BigDecimal(currentSessions)))
        val denominator = previousTotal.multiply(BigDecimal(currentSessions))
        return numerator.multiply(BigDecimal(100)).divide(denominator, 1, RoundingMode.HALF_UP)
    }

    // 빈도 문장: 횟수가 다르면 "지난주 3회 → 이번 주 4회", 같고 최소 횟수 이상이면 "이번 주도 3회, 꾸준해요". 어느 한쪽 횟수를 모르면 만들지 않는다
    private fun frequencyInsight(current: Int?, previous: Int?, labels: PeriodLabels): Insight? = when {
        current == null || previous == null -> null
        current != previous -> Insight(id = null, summaryText = "${labels.previous} ${previous}회 → ${labels.current} ${current}회")
        current >= labels.steadyMinSessions -> Insight(id = null, summaryText = "${labels.current}도 ${current}회, 꾸준해요")
        else -> null
    }

    // 강도 증감 문장: ±10% 이상일 때만 만들고, 100%를 넘는 증가는 숫자 없이 쓴다 (subject 예: "세션당 볼륨이")
    private fun changeInsight(change: BigDecimal?, subject: String, labels: PeriodLabels): Insight? = when {
        change == null -> null
        change > LARGE_INCREASE_THRESHOLD ->
            Insight(id = null, summaryText = "${labels.current} $subject ${labels.previous}보다 크게 늘었어요")
        change >= CHANGE_THRESHOLD ->
            Insight(id = null, summaryText = "${labels.current} $subject ${labels.previous}보다 ${change}% 증가했어요")
        change <= CHANGE_THRESHOLD.negate() ->
            Insight(id = null, summaryText = "${labels.current} $subject ${labels.previous}보다 ${change.abs()}% 감소했어요")
        else -> null
    }

    // 세션 하나의 볼륨: 완료된 STRENGTH 로그의 볼륨 합
    private fun sessionVolume(session: WorkoutSession, exercisesById: Map<UUID, Exercise>): BigDecimal =
        session.logs
            .filter { it.completed && exercisesById.getValue(it.exerciseId).type == ExerciseType.STRENGTH }
            .fold(BigDecimal.ZERO) { acc, log -> acc + volumeOf(log) }

    // 세션 하나의 맨몸 반복수: 완료된 BODYWEIGHT STRENGTH 로그의 sets*reps 합
    private fun sessionBodyweightReps(session: WorkoutSession, exercisesById: Map<UUID, Exercise>): Int =
        session.logs
            .filter { log ->
                val exercise = exercisesById.getValue(log.exerciseId)
                log.completed && exercise.type == ExerciseType.STRENGTH && exercise.equipment == Equipment.BODYWEIGHT
            }
            .sumOf { (it.actualSets ?: 0) * (it.actualReps ?: 0) }

    // SessionLog 하나의 볼륨(무게*횟수*세트)을 계산
    private fun volumeOf(log: SessionLog): BigDecimal {
        val sets = BigDecimal(log.actualSets ?: 0)
        val reps = BigDecimal(log.actualReps ?: 0)
        val weight = log.actualWeight ?: BigDecimal.ZERO
        return sets.multiply(reps).multiply(weight)
    }
}
