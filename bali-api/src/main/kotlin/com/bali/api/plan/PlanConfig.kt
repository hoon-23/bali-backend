package com.bali.api.plan

import com.bali.core.plan.PlanLimits
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// 설정(bali.plan.*)에서 무료 플랜 한도 정책을 만든다. limits-enabled 기본값은 false(무제한)
@Configuration
class PlanConfig {

    @Bean
    fun planLimits(
        @Value("\${bali.plan.limits-enabled:false}") enabled: Boolean,
        @Value("\${bali.plan.free.max-templates:5}") maxTemplates: Int,
        @Value("\${bali.plan.free.max-personal-exercises:10}") maxPersonalExercises: Int,
        @Value("\${bali.plan.free.monthly-insights-allowed:false}") monthlyInsightsAllowed: Boolean,
    ): PlanLimits = PlanLimits(enabled, maxTemplates, maxPersonalExercises, monthlyInsightsAllowed)
}
