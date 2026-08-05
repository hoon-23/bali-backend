package com.bali.infra.exercise

import com.bali.core.exercise.Exercise
import com.bali.core.exercise.ExerciseRepository
import org.springframework.stereotype.Repository
import java.util.UUID

// JPA 백엔드로 ExerciseRepository 포트를 구현하는 Spring 리포지토리 어댑터.
@Repository
class ExerciseRepositoryAdapter(
    private val jpaRepository: ExerciseJpaRepository,
) : ExerciseRepository {

    // 고유 식별자로 종목을 조회.
    override fun findById(id: UUID): Exercise? =
        jpaRepository.findById(id).orElse(null)?.toDomain()

    // userId가 볼 수 있는 종목 목록 조회.
    override fun findVisibleTo(userId: UUID): List<Exercise> =
        jpaRepository.findVisibleTo(userId).map { it.toDomain() }

    // 이름 유사도 기준 상위 종목 제안.
    override fun suggest(query: String, userId: UUID, limit: Int): List<Exercise> =
        jpaRepository.suggest(query, userId, limit).map { it.toDomain() }

    // 종목을 저장하고 ID가 할당된 도메인 엔티티를 반환.
    override fun save(exercise: Exercise): Exercise {
        // ID가 없으면 새 UUID 할당, 있으면 기존 값 사용.
        val entity = ExerciseJpaEntity(
            id = exercise.id ?: UUID.randomUUID(),
            name = exercise.name,
            variant = exercise.variant,
            muscleGroup = exercise.muscleGroup,
            type = exercise.type,
            scope = exercise.scope,
            ownerId = exercise.ownerId,
        )
        return jpaRepository.save(entity).toDomain()
    }

    // JPA 엔티티를 도메인 모델로 변환.
    private fun ExerciseJpaEntity.toDomain() = Exercise(
        id = id,
        name = name,
        variant = variant,
        muscleGroup = muscleGroup,
        type = type,
        scope = scope,
        ownerId = ownerId,
    )
}
