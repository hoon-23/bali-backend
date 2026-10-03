package com.bali.core.plan

import java.time.Instant

// 사용자 요금제. 결제 연동 전에는 모든 사용자가 FREE다
enum class Plan { FREE, PRO }

// plan이 PRO여도 만료 시각이 지났으면 FREE로 본다 (만료 시각이 null이면 평생 이용권처럼 만료 없음)
fun effectivePlan(plan: Plan, planExpiresAt: Instant?, now: Instant): Plan =
    if (plan == Plan.PRO && (planExpiresAt == null || planExpiresAt.isAfter(now))) Plan.PRO else Plan.FREE
