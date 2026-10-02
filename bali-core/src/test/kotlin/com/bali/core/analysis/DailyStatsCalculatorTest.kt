package com.bali.core.analysis

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

class DailyStatsCalculatorTest : StringSpec({

    val benchPressId = UUID.randomUUID()
    val runningId = UUID.randomUUID()
    val userId = UUID.randomUUID()

    val exercisesById = mapOf(
        benchPressId to Exercise(id = benchPressId, name = "test", variant = null, muscleGroup = MuscleGroup.CHEST, type = ExerciseType.STRENGTH, scope = ExerciseScope.GLOBAL, ownerId = null),
        runningId to Exercise(id = runningId, name = "test", variant = null, muscleGroup = MuscleGroup.CARDIO, type = ExerciseType.CARDIO, scope = ExerciseScope.GLOBAL, ownerId = null),
    )

    fun strengthLog(sets: Int = 3, reps: Int = 10, weight: String = "60.0") =
        SessionLog.create(ExerciseType.STRENGTH, benchPressId, sortOrder = 0, targetSets = sets, targetReps = reps, targetWeight = BigDecimal(weight))
            .copy(completed = true, actualSets = sets, actualReps = reps, actualWeight = BigDecimal(weight))

    fun cardioLog(durationSeconds: Int = 1800) =
        SessionLog.create(ExerciseType.CARDIO, runningId, sortOrder = 0, targetDurationSeconds = durationSeconds)
            .copy(completed = true, actualDurationSeconds = durationSeconds)

    fun session(date: LocalDate, logs: List<SessionLog>) =
        WorkoutSession(id = UUID.randomUUID(), userId = userId, date = date, templateId = null, status = SessionStatus.COMPLETED, logs = logs)

    "세션들을 날짜별로 그룹핑해 일별 통계를 계산한다" {
        val day1 = LocalDate.of(2026, 8, 3)
        val day2 = LocalDate.of(2026, 8, 4)
        val sessions = listOf(
            session(day1, listOf(strengthLog(sets = 3))),
            session(day2, listOf(cardioLog(durationSeconds = 1800))),
        )

        val result = DailyStatsCalculator.calculate(sessions, exercisesById)

        result.size shouldBe 2
        result[0].date shouldBe day1
        result[0].totalMinutes shouldBe 12
        result[0].completedSets shouldBe 3
        result[1].date shouldBe day2
        result[1].totalMinutes shouldBe 30
        result[1].completedSets shouldBe 1
    }

    "같은 날짜의 여러 세션은 하나의 DailyStats로 합산된다" {
        val date = LocalDate.of(2026, 8, 3)
        val sessions = listOf(
            session(date, listOf(strengthLog(sets = 3))),
            session(date, listOf(cardioLog(durationSeconds = 600))),
        )

        val result = DailyStatsCalculator.calculate(sessions, exercisesById)

        result.size shouldBe 1
        result[0].sessionsCount shouldBe 2
        result[0].totalMinutes shouldBe 22
        result[0].completedSets shouldBe 4
    }

    "완료되지 않은 로그는 집계에서 제외된다" {
        val date = LocalDate.of(2026, 8, 3)
        val incompleteLog = SessionLog.create(ExerciseType.STRENGTH, benchPressId, sortOrder = 0, targetSets = 3, targetReps = 10, targetWeight = BigDecimal("60.0"))
        val sessions = listOf(session(date, listOf(incompleteLog)))

        val result = DailyStatsCalculator.calculate(sessions, exercisesById)

        result[0].totalMinutes shouldBe 0
        result[0].completedSets shouldBe 0
    }

    "기록 없는 날짜는 결과에 포함되지 않는다" {
        DailyStatsCalculator.calculate(emptyList(), exercisesById) shouldBe emptyList()
    }

    "기준일 이후 날짜는 기록이 있는 세션만 세고 그런 세션이 없으면 날짜가 빠진다" {
        val strict = WorkoutRecordPolicy.STRICT_FROM
        val checkedOnly = strengthLog().copy(actualSets = 0, actualReps = 0)
        val sessions = listOf(
            session(strict, listOf(checkedOnly)),
            session(strict.plusDays(1), listOf(strengthLog(sets = 3))),
            session(strict.plusDays(1), listOf(checkedOnly)),
        )

        val result = DailyStatsCalculator.calculate(sessions, exercisesById)

        result.map { it.date } shouldBe listOf(strict.plusDays(1))
        result.single().sessionsCount shouldBe 1
        result.single().totalMinutes shouldBe 12
        result.single().completedSets shouldBe 3
    }

    "기준일 이전 날짜는 소급하지 않아 로그 없는 세션만 있어도 날짜가 남는다" {
        val before = WorkoutRecordPolicy.STRICT_FROM.minusDays(1)
        val checkedOnly = strengthLog().copy(actualSets = null, actualReps = null)
        val sessions = listOf(session(before.minusDays(1), emptyList()), session(before, listOf(checkedOnly)))

        val result = DailyStatsCalculator.calculate(sessions, exercisesById)

        result.map { it.date } shouldBe listOf(before.minusDays(1), before)
        result.map { it.sessionsCount } shouldBe listOf(1, 1)
        result[1].totalMinutes shouldBe 12
    }
})
