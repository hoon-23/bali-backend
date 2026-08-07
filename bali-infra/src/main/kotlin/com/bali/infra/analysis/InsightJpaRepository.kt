package com.bali.infra.analysis

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface InsightJpaRepository : JpaRepository<InsightJpaEntity, UUID> {
    // 특정 분석 결과에 속한 인사이트 전체 조회
    fun findByAnalysisId(analysisId: UUID): List<InsightJpaEntity>

    // 여러 분석 결과에 속한 인사이트를 한 번에 조회 (N+1 방지)
    fun findByAnalysisIdIn(analysisIds: List<UUID>): List<InsightJpaEntity>

    // 전체 재계산(save)을 위해 기존 인사이트를 모두 삭제
    @Modifying
    @Query("DELETE FROM InsightJpaEntity i WHERE i.analysisId = :analysisId")
    fun deleteByAnalysisId(@Param("analysisId") analysisId: UUID)
}
