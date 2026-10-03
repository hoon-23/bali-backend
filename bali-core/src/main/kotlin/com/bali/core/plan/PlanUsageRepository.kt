package com.bali.core.plan

import java.util.UUID

// 한도 판정용 플랜/사용량 조회 포트. 여러 집계를 한 번의 DB 왕복으로 읽는다
interface PlanUsageRepository {
    // 플랜과 루틴 수/직접 만든 운동 수를 한 쿼리로 조회, 사용자가 없으면 null
    fun findPlanUsage(userId: UUID): PlanUsage?
}
