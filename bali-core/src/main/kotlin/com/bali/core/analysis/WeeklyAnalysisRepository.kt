package com.bali.core.analysis

import java.time.LocalDate
import java.util.UUID

// 주간 분석 결과 영속성을 위한 포트 인터페이스
interface WeeklyAnalysisRepository {
    // 특정 유저의 특정 주 분석 결과를 조회
    fun findByUserIdAndWeekOf(userId: UUID, weekOf: LocalDate): WeeklyAnalysis?

    // 특정 유저의 전체 분석 결과를 weekOf 내림차순으로 조회
    fun findAllByUserId(userId: UUID): List<WeeklyAnalysis>

    // 분석 결과를 저장 (동일 (userId, weekOf) 기존 레코드가 있으면 삭제 후 재생성 — 전체 재계산 방식)
    fun save(analysis: WeeklyAnalysis): WeeklyAnalysis
}
