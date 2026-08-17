package com.bali.core.user

import java.time.LocalDate

// 활동한 날짜 집합으로부터 연속운동일(streak)을 계산하는 순수 함수
object StreakCalculator {
    // activeDates: 완료된 운동 기록이 있는 날짜 집합. 최근 활동일이 어제보다 오래됐으면 0(끊김),
    // 오늘 기록이 없어도 어제까지 연속이면 안 끊긴 것으로 취급한다
    fun calculate(activeDates: Set<LocalDate>, today: LocalDate): Int {
        val mostRecent = activeDates.maxOrNull() ?: return 0
        if (mostRecent.isBefore(today.minusDays(1))) return 0

        var streak = 0
        var cursor = mostRecent
        while (cursor in activeDates) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }
}
