package com.bali.infra.exercise

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ExerciseJpaRepository : JpaRepository<ExerciseJpaEntity, UUID> {

    // userId가 볼 수 있는 종목 목록 (GLOBAL 전체 + 본인 PERSONAL)
    @Query("SELECT e FROM ExerciseJpaEntity e WHERE e.scope = 'GLOBAL' OR (e.scope = 'PERSONAL' AND e.ownerId = :userId)")
    fun findVisibleTo(@Param("userId") userId: UUID): List<ExerciseJpaEntity>

    // trigram 유사도 기준으로 정렬된 상위 종목 제안 (GLOBAL 전체 + 본인 PERSONAL)
    @Query(
        value = """
            SELECT * FROM exercises
            WHERE (scope = 'GLOBAL' OR (scope = 'PERSONAL' AND owner_id = :userId))
              AND similarity(name, :query) > 0.2
            ORDER BY similarity(name, :query) DESC
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun suggest(
        @Param("query") query: String,
        @Param("userId") userId: UUID,
        @Param("limit") limit: Int,
    ): List<ExerciseJpaEntity>
}
