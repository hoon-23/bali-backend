package com.bali.infra.session

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface SessionLogJpaRepository : JpaRepository<SessionLogJpaEntity, UUID> {
    // 특정 세션에 속한 모든 log를 sortOrder 오름차순으로 조회
    fun findBySessionIdOrderBySortOrderAsc(sessionId: UUID): List<SessionLogJpaEntity>

    // 여러 세션의 log를 sortOrder 오름차순으로 한 번에 조회 (목록 조회 시 N+1 방지용 배치 조회)
    fun findBySessionIdInOrderBySortOrderAsc(sessionIds: List<UUID>): List<SessionLogJpaEntity>

    // 지정한 logId들을 삭제
    @Modifying
    @Query("DELETE FROM SessionLogJpaEntity l WHERE l.id IN :logIds")
    fun deleteByIdIn(@Param("logIds") logIds: List<UUID>)
}
