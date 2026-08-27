package com.bali.infra.exercise

import com.bali.core.exercise.Exercise
import com.bali.core.exercise.ExerciseRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
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

    // 고유 식별자로 종목을 조회하되, userId가 볼 수 있는 종목이 아니면 null 반환.
    override fun findVisibleTo(id: UUID, userId: UUID): Exercise? =
        jpaRepository.findVisibleTo(id, userId)?.toDomain()

    // 이름 유사도 기준 상위 종목 제안. SET LOCAL(트랜잭션 범위 임계값 설정)과 조회 쿼리가
    // 반드시 같은 트랜잭션/커넥션에서 실행되도록 이 메서드 전체를 하나의 트랜잭션으로 묶는다.
    @Transactional
    override fun suggest(query: String, userId: UUID, limit: Int): List<Exercise> {
        jpaRepository.setSuggestSimilarityThreshold()
        return jpaRepository.suggest(query, userId, limit).map { it.toDomain() }
    }

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
            equipment = exercise.equipment,
        )
        return jpaRepository.save(entity).toDomain()
    }

    // 종목을 삭제.
    override fun deleteById(id: UUID) {
        jpaRepository.deleteExerciseById(id)
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
        equipment = equipment,
    )
}
