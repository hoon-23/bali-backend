package com.bali.core.template

import com.bali.core.exercise.ExerciseType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.util.UUID

class TemplateItemTest : StringSpec({

    "STRENGTH 종목은 sets/reps/weight가 모두 있으면 정상 생성된다" {
        val item = TemplateItem.create(
            exerciseType = ExerciseType.STRENGTH,
            exerciseId = UUID.randomUUID(),
            sortOrder = 0,
            targetSets = 3,
            targetReps = 10,
            targetWeight = BigDecimal("60.0"),
        )
        item.targetSets shouldBe 3
    }

    "STRENGTH 종목에 targetWeight가 없으면 예외가 발생한다" {
        shouldThrow<IllegalArgumentException> {
            TemplateItem.create(
                exerciseType = ExerciseType.STRENGTH,
                exerciseId = UUID.randomUUID(),
                sortOrder = 0,
                targetSets = 3,
                targetReps = 10,
            )
        }
    }

    "STRENGTH 종목에 targetDurationSeconds가 있으면 예외가 발생한다" {
        shouldThrow<IllegalArgumentException> {
            TemplateItem.create(
                exerciseType = ExerciseType.STRENGTH,
                exerciseId = UUID.randomUUID(),
                sortOrder = 0,
                targetSets = 3,
                targetReps = 10,
                targetWeight = BigDecimal("60.0"),
                targetDurationSeconds = 60,
            )
        }
    }

    "CARDIO 종목은 targetDurationSeconds가 있으면 정상 생성된다" {
        val item = TemplateItem.create(
            exerciseType = ExerciseType.CARDIO,
            exerciseId = UUID.randomUUID(),
            sortOrder = 0,
            targetDurationSeconds = 1800,
            targetPace = "5'30\"/km",
        )
        item.targetDurationSeconds shouldBe 1800
    }

    "CARDIO 종목에 targetDurationSeconds가 없으면 예외가 발생한다" {
        shouldThrow<IllegalArgumentException> {
            TemplateItem.create(
                exerciseType = ExerciseType.CARDIO,
                exerciseId = UUID.randomUUID(),
                sortOrder = 0,
            )
        }
    }

    "CARDIO 종목에 targetSets가 있으면 예외가 발생한다" {
        shouldThrow<IllegalArgumentException> {
            TemplateItem.create(
                exerciseType = ExerciseType.CARDIO,
                exerciseId = UUID.randomUUID(),
                sortOrder = 0,
                targetSets = 3,
                targetDurationSeconds = 1800,
            )
        }
    }
})
