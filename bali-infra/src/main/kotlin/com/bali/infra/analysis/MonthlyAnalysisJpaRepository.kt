package com.bali.infra.analysis

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.UUID

interface MonthlyAnalysisJpaRepository : JpaRepository<MonthlyAnalysisJpaEntity, UUID> {
    // 특정 유저의 특정 월 분석 결과 조회
    fun findByUserIdAndMonthOf(userId: UUID, monthOf: LocalDate): MonthlyAnalysisJpaEntity?

    // 특정 유저의 전체 분석 결과를 monthOf 내림차순으로 조회
    fun findAllByUserIdOrderByMonthOfDesc(userId: UUID): List<MonthlyAnalysisJpaEntity>
}
