package com.bali.core.analysis

import java.time.LocalDate
import java.util.UUID

// 월간 분석 결과 영속성을 위한 포트 인터페이스
interface MonthlyAnalysisRepository {
    // 특정 유저의 특정 월 분석 결과를 조회 (monthOf는 해당 월 1일)
    fun findByUserIdAndMonthOf(userId: UUID, monthOf: LocalDate): MonthlyAnalysis?

    // 특정 유저의 전체 분석 결과를 monthOf 내림차순으로 조회
    fun findAllByUserId(userId: UUID): List<MonthlyAnalysis>

    // 분석 결과를 저장 (동일 (userId, monthOf) 기존 레코드가 있으면 삭제 후 재생성 — 전체 재계산 방식)
    fun save(analysis: MonthlyAnalysis): MonthlyAnalysis
}
