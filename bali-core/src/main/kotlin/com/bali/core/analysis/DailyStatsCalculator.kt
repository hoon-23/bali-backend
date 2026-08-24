package com.bali.core.analysis

import com.bali.core.exercise.Exercise
import com.bali.core.exercise.ExerciseType
import com.bali.core.session.WorkoutSession
import java.time.LocalDate
import java.util.UUID

// WorkoutSession 목록 + 종목 메타데이터로부터 날짜별 집계값을 계산하는 순수 함수 모음
object DailyStatsCalculator {

    // 완료된 STRENGTH 로그 1개당 추정 소요 시간(분). WeeklyStatsCalculator와 동일한 추정치를 사용
    private const val MINUTES_PER_STRENGTH_EXERCISE = 12

    // sessions를 날짜별로 그룹핑해 일별 통계를 계산. 기록 없는 날짜는 결과에 포함하지 않는다(sparse)
    fun calculate(sessions: List<WorkoutSession>, exercisesById: Map<UUID, Exercise>): List<DailyStats> =
        sessions.groupBy { it.date }
            .map { (date, sessionsOnDate) -> calculateForDate(date, sessionsOnDate, exercisesById) }
            .sortedBy { it.date }

    private fun calculateForDate(date: LocalDate, sessions: List<WorkoutSession>, exercisesById: Map<UUID, Exercise>): DailyStats {
        val completedLogs = sessions.flatMap { it.logs }.filter { it.completed }
        val strengthLogs = completedLogs.filter { exercisesById.getValue(it.exerciseId).type == ExerciseType.STRENGTH }
        val cardioLogs = completedLogs.filter { exercisesById.getValue(it.exerciseId).type == ExerciseType.CARDIO }

        val cardioMinutes = cardioLogs.sumOf { it.actualDurationSeconds ?: 0 } / 60
        val totalMinutes = strengthLogs.size * MINUTES_PER_STRENGTH_EXERCISE + cardioMinutes

        // CARDIO 로그는 세트 개념이 없어 완료 1건당 1로 집계한다 (유산소만 한 날에도 히트맵 강도가 0이 되지 않도록)
        val completedSets = strengthLogs.sumOf { it.actualSets ?: 0 } + cardioLogs.size

        return DailyStats(
            date = date,
            totalMinutes = totalMinutes,
            sessionsCount = sessions.size,
            completedSets = completedSets,
        )
    }
}
