package com.bali.core.plan

import java.time.Instant

// 한도에 걸린 기능 종류. API 응답의 limit 필드로 노출된다
enum class LimitType { TEMPLATE_COUNT, PERSONAL_EXERCISE_COUNT, MONTHLY_INSIGHTS }

// 무료 플랜 한도를 넘는 요청일 때 던지는 예외 (API에서 403 LIMIT_EXCEEDED로 변환)
class LimitExceededException(val limit: LimitType, message: String) : RuntimeException(message)

// 한도 판정에 필요한 사용자의 플랜과 현재 사용량
data class PlanUsage(
    val plan: Plan,
    val planExpiresAt: Instant?,
    val templateCount: Int,
    val personalExerciseCount: Int,
)

// 플랜별 한도 정책. enabled=false면 모든 사용자가 무제한이다 (기본값 꺼짐, 설정으로 켠다)
class PlanLimits(
    val enabled: Boolean,
    val freeMaxTemplates: Int,
    val freeMaxPersonalExercises: Int,
    val freeMonthlyInsightsAllowed: Boolean,
) {
    // FREE이고 템플릿 수가 한도 이상이면 신규 생성을 막는다
    fun checkTemplateCreate(usage: PlanUsage, now: Instant) {
        if (!enabled || effectivePlan(usage.plan, usage.planExpiresAt, now) == Plan.PRO) return
        if (usage.templateCount >= freeMaxTemplates) {
            throw LimitExceededException(LimitType.TEMPLATE_COUNT, "무료 플랜은 루틴을 최대 ${freeMaxTemplates}개까지 만들 수 있습니다")
        }
    }

    // FREE이고 직접 만든 운동 수가 한도 이상이면 신규 생성을 막는다
    fun checkPersonalExerciseCreate(usage: PlanUsage, now: Instant) {
        if (!enabled || effectivePlan(usage.plan, usage.planExpiresAt, now) == Plan.PRO) return
        if (usage.personalExerciseCount >= freeMaxPersonalExercises) {
            throw LimitExceededException(LimitType.PERSONAL_EXERCISE_COUNT, "무료 플랜은 직접 만든 운동을 최대 ${freeMaxPersonalExercises}개까지 만들 수 있습니다")
        }
    }

    // FREE는 월간 인사이트 조회가 허용되지 않으면 막는다
    fun checkMonthlyInsights(plan: Plan) {
        if (!enabled || plan == Plan.PRO || freeMonthlyInsightsAllowed) return
        throw LimitExceededException(LimitType.MONTHLY_INSIGHTS, "월간 인사이트는 유료 플랜에서 이용할 수 있습니다")
    }
}
