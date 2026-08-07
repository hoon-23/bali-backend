package com.bali.infra.analysis

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.UUID

interface WeeklyAnalysisJpaRepository : JpaRepository<WeeklyAnalysisJpaEntity, UUID> {
    // 특정 유저의 특정 주 분석 결과 조회
    fun findByUserIdAndWeekOf(userId: UUID, weekOf: LocalDate): WeeklyAnalysisJpaEntity?

    // 특정 유저의 전체 분석 결과를 weekOf 내림차순으로 조회
    fun findAllByUserIdOrderByWeekOfDesc(userId: UUID): List<WeeklyAnalysisJpaEntity>
}
