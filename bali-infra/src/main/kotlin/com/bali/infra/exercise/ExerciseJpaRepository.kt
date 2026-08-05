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
    // `similarity(name, :query) > 0.2` 함수 호출 predicate는 idx_exercises_name_trgm GIN
    // 인덱스를 탈 수 없어(EXPLAIN으로 Seq Scan 확인됨) `%` 연산자로 바꿨다. `%`의 임계값은
    // 세션 GUC(pg_trgm.similarity_threshold, 커넥션 풀에서 SET이 깨지기 쉬움) 대신
    // set_limit()을 FROM 절 서브쿼리로 매 쿼리마다 적용해 0.2를 보장한다.
    // 주의: 2~3글자 한글 짧은 단어(예: "밴치"→"벤치프레스")는 자모 분해 없이 음절 단위로
    // trigram을 만들기 때문에 유사도가 정확히 0.0이 되어 임계값을 아무리 낮춰도 잡히지
    // 않는 pg_trgm의 근본적 한계다 (docs/specs/2026-08-05-exercise-catalog-design.md 참고).
    @Query(
        value = """
            SELECT e.* FROM exercises e, (SELECT set_limit(0.2)) AS threshold
            WHERE (e.scope = 'GLOBAL' OR (e.scope = 'PERSONAL' AND e.owner_id = :userId))
              AND e.name % :query
            ORDER BY similarity(e.name, :query) DESC
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
