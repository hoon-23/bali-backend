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

class MonthlyStatsCalculatorTest : StringSpec({

    val benchPressId = UUID.randomUUID()
    val burpeeId = UUID.randomUUID()

    fun exercise(id: UUID, muscleGroup: MuscleGroup, equipment: Equipment? = null) = Exercise(
        id = id, name = "test", variant = null, muscleGroup = muscleGroup, type = ExerciseType.STRENGTH,
        scope = ExerciseScope.GLOBAL, ownerId = null, equipment = equipment,
    )

    val exercisesById = mapOf(
        benchPressId to exercise(benchPressId, MuscleGroup.CHEST),
        burpeeId to exercise(burpeeId, MuscleGroup.FUNCTIONAL, Equipment.BODYWEIGHT),
    )

    fun strengthLog(exerciseId: UUID, sets: Int, reps: Int, weight: String) =
        SessionLog.create(ExerciseType.STRENGTH, exerciseId, sortOrder = 0, targetSets = sets, targetReps = reps, targetWeight = BigDecimal(weight))
            .copy(completed = true, actualSets = sets, actualReps = reps, actualWeight = BigDecimal(weight))

    "맨몸 종목은 bodyweightRepsByExercise에 집계되고 setsByMuscleGroup은 중량/맨몸을 함께 센다" {
        val logs = listOf(strengthLog(benchPressId, 3, 10, "60.0"), strengthLog(burpeeId, 4, 10, "0"))

        val summary = MonthlyStatsCalculator.calculate(logs, exercisesById, previousSummary = null)

        summary.bodyweightRepsByExercise shouldBe mapOf(burpeeId to 40)
        summary.setsByMuscleGroup shouldBe mapOf(MuscleGroup.CHEST to 3, MuscleGroup.FUNCTIONAL to 4)
    }

    "지난달 맨몸 반복수 대비 증감률이 계산되고 없거나 0이면 null이다" {
        val logs = listOf(strengthLog(burpeeId, 3, 20, "0"))
        val previous = AnalysisSummary(0, emptyMap(), emptyMap(), 0, BigDecimal.ZERO, null, bodyweightRepsByExercise = mapOf(burpeeId to 40))
        val noPrevious = AnalysisSummary(0, emptyMap(), emptyMap(), 0, BigDecimal.ZERO, null)

        MonthlyStatsCalculator.calculate(logs, exercisesById, previous).bodyweightRepsChangeFromLastWeekPercent shouldBe BigDecimal("50.0")
        MonthlyStatsCalculator.calculate(logs, exercisesById, noPrevious).bodyweightRepsChangeFromLastWeekPercent shouldBe null
    }

    "맨몸 반복수 증감 인사이트는 이번 달/지난달 문구를 쓴다" {
        val up = AnalysisSummary(60, emptyMap(), emptyMap(), 0, BigDecimal("100.0"), null, bodyweightRepsChangeFromLastWeekPercent = BigDecimal("50.0"))
        val down = AnalysisSummary(60, emptyMap(), emptyMap(), 0, BigDecimal("100.0"), null, bodyweightRepsChangeFromLastWeekPercent = BigDecimal("-20.0"))

        MonthlyStatsCalculator.generateInsights(up).map { it.summaryText } shouldBe listOf("이번 달 맨몸 운동 반복수가 지난달보다 50.0% 증가했어요")
        MonthlyStatsCalculator.generateInsights(down).map { it.summaryText } shouldBe listOf("이번 달 맨몸 운동 반복수가 지난달보다 20.0% 감소했어요")
    }

    "무게 볼륨이 0인 맨몸 근육군도 세트 비중이 충분하면 불균형 인사이트가 생성되지 않는다" {
        val summary = AnalysisSummary(
            60, emptyMap(), mapOf(MuscleGroup.CHEST to BigDecimal("900.0"), MuscleGroup.FUNCTIONAL to BigDecimal.ZERO),
            0, BigDecimal("100.0"), null,
            setsByMuscleGroup = mapOf(MuscleGroup.CHEST to 5, MuscleGroup.FUNCTIONAL to 5),
        )

        MonthlyStatsCalculator.generateInsights(summary).isEmpty() shouldBe true
    }

    "중단 세션의 로그는 완료율에서 제외되지만 완료한 로그의 볼륨은 그대로 포함된다" {
        fun incompleteLog(exerciseId: UUID) =
            SessionLog.create(ExerciseType.STRENGTH, exerciseId, sortOrder = 0, targetSets = 3, targetReps = 10, targetWeight = BigDecimal("60.0"))
                .copy(id = UUID.randomUUID())
        val doneNormal = strengthLog(benchPressId, 3, 10, "60.0").copy(id = UUID.randomUUID())
        val undoneNormal = incompleteLog(benchPressId)
        val undoneAbandoned = incompleteLog(benchPressId)
        val doneAbandoned = strengthLog(benchPressId, 3, 10, "60.0").copy(id = UUID.randomUUID())
        val logs = listOf(doneNormal, undoneNormal, undoneAbandoned, doneAbandoned)

        val summary = MonthlyStatsCalculator.calculate(
            logs, exercisesById, previousSummary = null,
            abandonedLogIds = setOf(undoneAbandoned.id!!, doneAbandoned.id!!),
        )

        summary.completionRate shouldBe BigDecimal("50.0")
        summary.volumeByExercise[benchPressId] shouldBe BigDecimal("3600.0")
    }
})
