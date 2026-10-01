package com.bali.core.analysis

import com.bali.core.exercise.Equipment
import com.bali.core.exercise.Exercise
import com.bali.core.exercise.ExerciseScope
import com.bali.core.exercise.ExerciseType
import com.bali.core.exercise.MuscleGroup
import com.bali.core.session.SessionLog
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
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

    "완료된 STRENGTH 로그의 볼륨은 sets*reps*weight로 종목별/근육군별 합산된다" {
        val logs = listOf(strengthLog(benchPressId, completed = true, sets = 3, reps = 10, weight = "60.0"))

        val summary = WeeklyStatsCalculator.calculate(logs, exercisesById, previousSummary = null)

        summary.volumeByExercise[benchPressId] shouldBe BigDecimal("1800.0")
        summary.volumeByMuscleGroup[MuscleGroup.CHEST] shouldBe BigDecimal("1800.0")
    }

    "완료되지 않은 로그는 볼륨/유산소 시간 계산에서 제외된다" {
        val logs = listOf(strengthLog(benchPressId, completed = false), cardioLog(runningId, completed = false))

        val summary = WeeklyStatsCalculator.calculate(logs, exercisesById, previousSummary = null)

        summary.volumeByExercise.isEmpty() shouldBe true
        summary.cardioTotalMinutes shouldBe 0
    }

    "완료된 CARDIO 로그의 duration은 분 단위로 합산된다" {
        val logs = listOf(cardioLog(runningId, completed = true, durationSeconds = 1800))

        val summary = WeeklyStatsCalculator.calculate(logs, exercisesById, previousSummary = null)

        summary.cardioTotalMinutes shouldBe 30
    }

    "totalWorkoutMinutes는 완료된 STRENGTH 로그 수*12 + cardioTotalMinutes다" {
        val logs = listOf(
            strengthLog(benchPressId, completed = true), strengthLog(squatId, completed = true),
            cardioLog(runningId, completed = true, durationSeconds = 600),
        )

        val summary = WeeklyStatsCalculator.calculate(logs, exercisesById, previousSummary = null)

        summary.totalWorkoutMinutes shouldBe (2 * 12 + 10)
    }

    "completionRate는 완료 로그 수/전체 로그 수 * 100이다" {
        val logs = listOf(strengthLog(benchPressId, completed = true), strengthLog(squatId, completed = false))

        val summary = WeeklyStatsCalculator.calculate(logs, exercisesById, previousSummary = null)

        summary.completionRate shouldBe BigDecimal("50.0")
    }

    "로그가 하나도 없으면 completionRate는 0이다" {
        val summary = WeeklyStatsCalculator.calculate(emptyList(), exercisesById, previousSummary = null)

        summary.completionRate shouldBe BigDecimal.ZERO
    }

    "지난주 요약이 없으면 volumeChangeFromLastWeekPercent는 null이다" {
        val logs = listOf(strengthLog(benchPressId, completed = true))

        val summary = WeeklyStatsCalculator.calculate(logs, exercisesById, previousSummary = null)

        summary.volumeChangeFromLastWeekPercent shouldBe null
    }

    "지난주 총 볼륨이 0이면 volumeChangeFromLastWeekPercent는 null이다" {
        val logs = listOf(strengthLog(benchPressId, completed = true))
        val previous = AnalysisSummary(0, emptyMap(), emptyMap(), 0, BigDecimal.ZERO, null)

        val summary = WeeklyStatsCalculator.calculate(logs, exercisesById, previousSummary = previous)

        summary.volumeChangeFromLastWeekPercent shouldBe null
    }

    "지난주 대비 볼륨 증감률이 올바르게 계산된다" {
        val logs = listOf(strengthLog(benchPressId, completed = true, sets = 3, reps = 10, weight = "60.0"))
        val previous = AnalysisSummary(0, mapOf(benchPressId to BigDecimal("1500.0")), emptyMap(), 0, BigDecimal.ZERO, null)

        val summary = WeeklyStatsCalculator.calculate(logs, exercisesById, previousSummary = previous)

        summary.volumeChangeFromLastWeekPercent shouldBe BigDecimal("20.0")
    }

    "볼륨이 지난주보다 10% 이상 늘면 증가 인사이트가 생성된다" {
        val summary = AnalysisSummary(60, emptyMap(), emptyMap(), 0, BigDecimal("100.0"), BigDecimal("20.0"))

        val insights = WeeklyStatsCalculator.generateInsights(summary)

        insights.map { it.summaryText } shouldBe listOf("이번 주 볼륨이 지난주보다 20.0% 증가했어요")
    }

    "볼륨이 지난주보다 10% 이상 줄면 감소 인사이트가 생성된다" {
        val summary = AnalysisSummary(60, emptyMap(), emptyMap(), 0, BigDecimal("100.0"), BigDecimal("-15.0"))

        val insights = WeeklyStatsCalculator.generateInsights(summary)

        insights.map { it.summaryText } shouldBe listOf("이번 주 볼륨이 지난주보다 15.0% 감소했어요")
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

        val summary = WeeklyStatsCalculator.calculate(logs, exercisesById, previousSummary = null)

        summary.bodyweightRepsByExercise shouldBe mapOf(burpeeId to 45)
        summary.volumeByExercise.getValue(burpeeId).compareTo(BigDecimal.ZERO) shouldBe 0
    }

    "중량 종목은 bodyweightRepsByExercise에 포함되지 않는다" {
        val logs = listOf(strengthLog(benchPressId, completed = true))

        val summary = WeeklyStatsCalculator.calculate(logs, exercisesById, previousSummary = null)

        summary.bodyweightRepsByExercise.isEmpty() shouldBe true
    }

    "완료되지 않았거나 reps가 비어 있는 맨몸 로그는 bodyweightRepsByExercise에서 제외된다" {
        val incomplete = strengthLog(burpeeId, completed = false, weight = "0")
        val noReps = strengthLog(burpeeId, completed = true, weight = "0").copy(actualReps = null)

        val summary = WeeklyStatsCalculator.calculate(listOf(incomplete, noReps), exercisesById, previousSummary = null)

        summary.bodyweightRepsByExercise.isEmpty() shouldBe true
    }

    "setsByMuscleGroup은 중량/맨몸 구분 없이 완료 세트를 근육군별로 합산한다" {
        val logs = listOf(
            strengthLog(benchPressId, completed = true, sets = 3),
            strengthLog(burpeeId, completed = true, sets = 4, weight = "0"),
            strengthLog(squatId, completed = false, sets = 5),
        )

        val summary = WeeklyStatsCalculator.calculate(logs, exercisesById, previousSummary = null)

        summary.setsByMuscleGroup shouldBe mapOf(MuscleGroup.CHEST to 3, MuscleGroup.FUNCTIONAL to 4)
    }

    "지난주 맨몸 반복수 대비 증감률이 올바르게 계산된다" {
        val logs = listOf(strengthLog(burpeeId, completed = true, sets = 3, reps = 15, weight = "0"))
        val previous = AnalysisSummary(0, emptyMap(), emptyMap(), 0, BigDecimal.ZERO, null, bodyweightRepsByExercise = mapOf(burpeeId to 30))

        val summary = WeeklyStatsCalculator.calculate(logs, exercisesById, previousSummary = previous)

        summary.bodyweightRepsChangeFromLastWeekPercent shouldBe BigDecimal("50.0")
    }

    "지난주 맨몸 반복수가 없거나 0이면 증감률은 null이다" {
        val logs = listOf(strengthLog(burpeeId, completed = true, weight = "0"))
        val previous = AnalysisSummary(0, emptyMap(), emptyMap(), 0, BigDecimal.ZERO, null)

        val summary = WeeklyStatsCalculator.calculate(logs, exercisesById, previousSummary = previous)

        summary.bodyweightRepsChangeFromLastWeekPercent shouldBe null
    }

    "맨몸 반복수가 지난주보다 10% 이상 늘면 증가 인사이트가 생성된다" {
        val summary = AnalysisSummary(60, emptyMap(), emptyMap(), 0, BigDecimal("100.0"), null, bodyweightRepsChangeFromLastWeekPercent = BigDecimal("50.0"))

        val insights = WeeklyStatsCalculator.generateInsights(summary)

        insights.map { it.summaryText } shouldBe listOf("이번 주 맨몸 운동 반복수가 지난주보다 50.0% 증가했어요")
    }

    "맨몸 반복수가 지난주보다 10% 이상 줄면 감소 인사이트가 생성된다" {
        val summary = AnalysisSummary(60, emptyMap(), emptyMap(), 0, BigDecimal("100.0"), null, bodyweightRepsChangeFromLastWeekPercent = BigDecimal("-20.0"))

        val insights = WeeklyStatsCalculator.generateInsights(summary)

        insights.map { it.summaryText } shouldBe listOf("이번 주 맨몸 운동 반복수가 지난주보다 20.0% 감소했어요")
    }

    "맨몸 반복수 증감이 10% 미만이면 인사이트가 생성되지 않는다" {
        val summary = AnalysisSummary(60, emptyMap(), emptyMap(), 0, BigDecimal("100.0"), null, bodyweightRepsChangeFromLastWeekPercent = BigDecimal("5.0"))

        WeeklyStatsCalculator.generateInsights(summary).isEmpty() shouldBe true
    }
})
