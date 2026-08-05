package com.bali.core.exercise

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

class ExerciseTest : StringSpec({

    // 테스트용 기본 GLOBAL 종목 생성
    fun globalExercise() = Exercise(
        id = UUID.randomUUID(),
        name = "벤치프레스",
        variant = null,
        muscleGroup = MuscleGroup.CHEST,
        type = ExerciseType.STRENGTH,
        scope = ExerciseScope.GLOBAL,
        ownerId = null,
    )

    "GLOBAL 종목은 ownerId가 null이면 정상 생성된다" {
        globalExercise().scope shouldBe ExerciseScope.GLOBAL
    }

    "GLOBAL 종목에 ownerId가 있으면 예외가 발생한다" {
        shouldThrow<IllegalArgumentException> {
            globalExercise().copy(ownerId = UUID.randomUUID())
        }
    }

    "PERSONAL 종목에 ownerId가 없으면 예외가 발생한다" {
        shouldThrow<IllegalArgumentException> {
            globalExercise().copy(scope = ExerciseScope.PERSONAL, ownerId = null)
        }
    }

    "PERSONAL 종목은 ownerId가 있으면 정상 생성된다" {
        val personal = globalExercise().copy(scope = ExerciseScope.PERSONAL, ownerId = UUID.randomUUID())
        personal.scope shouldBe ExerciseScope.PERSONAL
    }
})
