package com.bali.infra.exercise

import com.bali.core.exercise.ExerciseScope
import com.bali.core.exercise.ExerciseType
import com.bali.core.exercise.MuscleGroup
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

// Exercise 도메인 모델 영속성을 위한 JPA 엔티티.
@Entity
@Table(name = "exercises")
class ExerciseJpaEntity(
    @Id
    var id: UUID = UUID.randomUUID(),

    var name: String = "",

    var variant: String? = null,

    @Enumerated(EnumType.STRING)
    var muscleGroup: MuscleGroup = MuscleGroup.BACK,

    @Enumerated(EnumType.STRING)
    var type: ExerciseType = ExerciseType.STRENGTH,

    @Enumerated(EnumType.STRING)
    var scope: ExerciseScope = ExerciseScope.GLOBAL,

    var ownerId: UUID? = null,
)

