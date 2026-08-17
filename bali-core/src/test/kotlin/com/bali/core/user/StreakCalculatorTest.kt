package com.bali.core.user

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class StreakCalculatorTest : StringSpec({

    val today = LocalDate.of(2026, 8, 17)

    "오늘 포함 연속 3일이면 3을 반환한다" {
        val activeDates = setOf(today, today.minusDays(1), today.minusDays(2))

        StreakCalculator.calculate(activeDates, today) shouldBe 3
    }

    "어제까지만 연속이고 오늘 기록이 없어도 끊기지 않는다" {
        val activeDates = setOf(today.minusDays(1), today.minusDays(2), today.minusDays(3))

        StreakCalculator.calculate(activeDates, today) shouldBe 3
    }

    "그제 이전이 마지막 활동일이면 0을 반환한다" {
        val activeDates = setOf(today.minusDays(2), today.minusDays(3))

        StreakCalculator.calculate(activeDates, today) shouldBe 0
    }

    "활동 기록이 없으면 0을 반환한다" {
        StreakCalculator.calculate(emptySet(), today) shouldBe 0
    }

    "중간에 공백이 있으면 최근 연속 구간만 카운트한다" {
        val activeDates = setOf(today, today.minusDays(1), today.minusDays(5), today.minusDays(6))

        StreakCalculator.calculate(activeDates, today) shouldBe 2
    }

    "미래 날짜는 무시하고 실제 연속 구간만 카운트한다" {
        val activeDates = setOf(today.plusDays(5), today, today.minusDays(1))

        StreakCalculator.calculate(activeDates, today) shouldBe 2
    }
})
