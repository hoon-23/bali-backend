package com.bali.core.session

import com.bali.core.exercise.ExerciseType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.util.UUID

class SessionLogTest : StringSpec({

    "target 전부 null이면 즉흥 추가 항목으로 정상 생성된다" {
        val log = SessionLog.create(
            exerciseType = ExerciseType.STRENGTH,
            exerciseId = UUID.randomUUID(),
            sortOrder = 0,
        )
        log.targetSets shouldBe null
        log.completed shouldBe false
    }

    "STRENGTH 종목에 targetSets만 채워져 있으면 정상 생성된다" {
        val log = SessionLog.create(
            exerciseType = ExerciseType.STRENGTH,
            exerciseId = UUID.randomUUID(),
            sortOrder = 0,
            targetSets = 3, targetReps = 10, targetWeight = BigDecimal("60.0"),
        )
        log.targetSets shouldBe 3
    }

    "STRENGTH 종목에 targetDurationSeconds가 섞이면 예외가 발생한다" {
        shouldThrow<IllegalArgumentException> {
            SessionLog.create(
                exerciseType = ExerciseType.STRENGTH,
                exerciseId = UUID.randomUUID(),
                sortOrder = 0,
                targetSets = 3, targetDurationSeconds = 60,
            )
        }
    }

    "CARDIO 종목에 targetSets가 섞이면 예외가 발생한다" {
        shouldThrow<IllegalArgumentException> {
            SessionLog.create(
                exerciseType = ExerciseType.CARDIO,
                exerciseId = UUID.randomUUID(),
                sortOrder = 0,
                targetDurationSeconds = 1800, targetSets = 3,
            )
        }
    }

    "validateActualFields는 STRENGTH 종목에 actualDurationSeconds가 있으면 예외를 던진다" {
        shouldThrow<IllegalArgumentException> {
            SessionLog.validateActualFields(
                exerciseType = ExerciseType.STRENGTH,
                actualSets = null, actualReps = null, actualWeight = null,
                actualDurationSeconds = 600, actualPace = null,
            )
        }
    }

    "validateActualFields는 전부 null이면 통과한다 (아직 미기록)" {
        SessionLog.validateActualFields(
            exerciseType = ExerciseType.CARDIO,
            actualSets = null, actualReps = null, actualWeight = null,
            actualDurationSeconds = null, actualPace = null,
        )
    }
})
