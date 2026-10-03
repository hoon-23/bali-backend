package com.bali.infra.plan

import com.bali.core.plan.PlanUsage
import com.bali.core.plan.PlanUsageRepository
import com.bali.infra.user.UserJpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

// UserJpaRepository의 집계 쿼리로 PlanUsageRepository 포트를 구현하는 어댑터
@Repository
class PlanUsageRepositoryAdapter(
    private val userJpaRepository: UserJpaRepository,
) : PlanUsageRepository {

    // 플랜과 사용량을 한 쿼리로 조회해 도메인 값으로 변환
    override fun findPlanUsage(userId: UUID): PlanUsage? =
        userJpaRepository.findPlanUsage(userId)?.let {
            PlanUsage(it.plan, it.planExpiresAt, it.templateCount.toInt(), it.personalExerciseCount.toInt())
        }
}
