package com.bali.api.plan

import com.bali.core.plan.PlanLimits
import com.bali.core.plan.PlanUsage
import com.bali.core.plan.PlanUsageRepository
import com.bali.core.user.UserRepository
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

// 컨트롤러가 한도가 걸린 기능을 실행하기 전에 호출하는 가드. 한도가 꺼져 있으면 DB 조회 없이 통과한다
@Component
class PlanGuard(
    private val limits: PlanLimits,
    private val planUsageRepository: PlanUsageRepository,
    private val userRepository: UserRepository,
) {

    // 루틴 생성 전 한도 확인
    fun assertCanCreateTemplate(userId: UUID) {
        if (!limits.enabled) return
        limits.checkTemplateCreate(usage(userId), Instant.now())
    }

    // 직접 만든 운동 생성 전 한도 확인
    fun assertCanCreatePersonalExercise(userId: UUID) {
        if (!limits.enabled) return
        limits.checkPersonalExerciseCreate(usage(userId), Instant.now())
    }

    // 월간 인사이트 조회 전 허용 여부 확인 (플랜만 필요하므로 PK 조회 1회)
    fun assertCanViewMonthlyInsights(userId: UUID) {
        if (!limits.enabled) return
        val user = checkNotNull(userRepository.findById(userId)) { "존재하지 않는 사용자: $userId" }
        limits.checkMonthlyInsights(user.effectivePlan())
    }

    // 플랜과 사용량을 한 쿼리로 조회
    private fun usage(userId: UUID): PlanUsage =
        checkNotNull(planUsageRepository.findPlanUsage(userId)) { "존재하지 않는 사용자: $userId" }
}
