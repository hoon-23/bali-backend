package com.bali.infra.exercise

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ExerciseJpaRepository : JpaRepository<ExerciseJpaEntity, UUID> {

    // userId가 볼 수 있는 종목 목록 (GLOBAL 전체 + 본인 PERSONAL)
    @Query("SELECT e FROM ExerciseJpaEntity e WHERE e.scope = 'GLOBAL' OR (e.scope = 'PERSONAL' AND e.ownerId = :userId)")
    fun findVisibleTo(@Param("userId") userId: UUID): List<ExerciseJpaEntity>

    // 고유 식별자로 조회하되 userId가 볼 수 있는(GLOBAL 전체 + 본인 PERSONAL) 종목만 대상으로 함
    @Query("SELECT e FROM ExerciseJpaEntity e WHERE e.id = :id AND (e.scope = 'GLOBAL' OR (e.scope = 'PERSONAL' AND e.ownerId = :userId))")
    fun findVisibleTo(@Param("id") id: UUID, @Param("userId") userId: UUID): ExerciseJpaEntity?

    // 트라이그램 유사도 임계값을 현재 트랜잭션(커넥션) 범위로만 설정한다. SET LOCAL은
    // 트랜잭션이 끝나면 자동으로 이전 값으로 복귀하므로 커넥션 풀에 부작용이 남지 않는다.
    // 반드시 ExerciseRepositoryAdapter.suggest()의 같은 @Transactional 안에서, suggest()
    // 쿼리보다 먼저(별도의 SQL 문으로) 호출되어야 한다 — 하나의 쿼리 안에서 서브쿼리로
    // set_limit()을 호출하는 방식은 플래너가 join 순서를 바꿀 수 있어(row 추정치가
    // 1일 때 실제로 재현됨) 일부 행에서 set_limit()이 `%` predicate 평가보다 늦게
    // 실행되는 버그가 있었다 (재검토에서 발견, "레그" 쿼리로 재현: 유사도 0.2857인
    // "레그프레스"가 간헐적으로 누락됨).
    @Modifying
    @Query(value = "SET LOCAL pg_trgm.similarity_threshold = 0.2", nativeQuery = true)
    fun setSuggestSimilarityThreshold()

    // trigram 유사도 기준으로 정렬된 상위 종목 제안 (GLOBAL 전체 + 본인 PERSONAL)
    // `similarity(name, :query) > 0.2` 함수 호출 predicate는 idx_exercises_name_trgm GIN
    // 인덱스를 탈 수 없어(EXPLAIN으로 Seq Scan 확인됨) `%` 연산자로 바꿨다. `%`의 임계값은
    // 반드시 setSuggestSimilarityThreshold()를 이 메서드보다 먼저 같은 트랜잭션에서 호출해
    // SET LOCAL로 미리 0.2로 설정해 둬야 한다.
    // 주의: 2~3글자 한글 짧은 단어(예: "밴치"→"벤치프레스")는 자모 분해 없이 음절 단위로
    // trigram을 만들기 때문에 유사도가 정확히 0.0이 되어 임계값을 아무리 낮춰도 잡히지
    // 않는 pg_trgm의 근본적 한계다 (docs/specs/2026-08-05-exercise-catalog-design.md 참고).
    @Query(
        value = """
            SELECT * FROM exercises
            WHERE (scope = 'GLOBAL' OR (scope = 'PERSONAL' AND owner_id = :userId))
              AND name % :query
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
