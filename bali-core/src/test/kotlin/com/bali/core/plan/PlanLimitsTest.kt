package com.bali.core.plan

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class PlanLimitsTest : StringSpec({

    val now = Instant.parse("2026-10-03T00:00:00Z")
    val limits = PlanLimits(enabled = true, freeMaxTemplates = 5, freeMaxPersonalExercises = 10, freeMonthlyInsightsAllowed = false)

    // 테스트용 사용량 생성
    fun usage(plan: Plan = Plan.FREE, expires: Instant? = null, templates: Int = 0, exercises: Int = 0) =
        PlanUsage(plan, expires, templates, exercises)

    "FREE는 루틴 4개까지 생성 가능하고 5개째부터 막힌다" {
        shouldNotThrowAny { limits.checkTemplateCreate(usage(templates = 4), now) }
        shouldThrow<LimitExceededException> { limits.checkTemplateCreate(usage(templates = 5), now) }.limit shouldBe LimitType.TEMPLATE_COUNT
    }

    "FREE는 직접 만든 운동 9개까지 생성 가능하고 10개째부터 막힌다" {
        shouldNotThrowAny { limits.checkPersonalExerciseCreate(usage(exercises = 9), now) }
        shouldThrow<LimitExceededException> { limits.checkPersonalExerciseCreate(usage(exercises = 10), now) }.limit shouldBe LimitType.PERSONAL_EXERCISE_COUNT
    }

    "PRO는 한도를 넘어도 생성 가능하다" {
        shouldNotThrowAny { limits.checkTemplateCreate(usage(Plan.PRO, templates = 99), now) }
        shouldNotThrowAny { limits.checkPersonalExerciseCreate(usage(Plan.PRO, exercises = 99), now) }
    }

    "만료된 PRO는 FREE로 취급되고, 만료 전 PRO와 만료 없는 PRO는 PRO다" {
        shouldThrow<LimitExceededException> { limits.checkTemplateCreate(usage(Plan.PRO, expires = now.minusSeconds(1), templates = 5), now) }
        shouldNotThrowAny { limits.checkTemplateCreate(usage(Plan.PRO, expires = now.plusSeconds(1), templates = 5), now) }
        shouldNotThrowAny { limits.checkTemplateCreate(usage(Plan.PRO, expires = null, templates = 5), now) }
    }

    "월간 인사이트는 FREE에서 막히고 PRO에서 허용된다" {
        shouldThrow<LimitExceededException> { limits.checkMonthlyInsights(Plan.FREE) }.limit shouldBe LimitType.MONTHLY_INSIGHTS
        shouldNotThrowAny { limits.checkMonthlyInsights(Plan.PRO) }
    }

    "freeMonthlyInsightsAllowed가 true면 FREE도 월간 인사이트를 볼 수 있다" {
        val open = PlanLimits(true, 5, 10, freeMonthlyInsightsAllowed = true)
        shouldNotThrowAny { open.checkMonthlyInsights(Plan.FREE) }
    }

    "enabled=false면 어떤 한도도 적용되지 않는다" {
        val off = PlanLimits(enabled = false, freeMaxTemplates = 5, freeMaxPersonalExercises = 10, freeMonthlyInsightsAllowed = false)
        shouldNotThrowAny { off.checkTemplateCreate(usage(templates = 99), now) }
        shouldNotThrowAny { off.checkPersonalExerciseCreate(usage(exercises = 99), now) }
        shouldNotThrowAny { off.checkMonthlyInsights(Plan.FREE) }
    }
})
