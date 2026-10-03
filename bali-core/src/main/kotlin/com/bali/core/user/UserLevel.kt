package com.bali.core.user

import com.bali.core.session.WorkoutRecordPolicy
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.pow
import kotlin.math.roundToInt

// 유저 레벨/경험치. XP는 저장하지 않고 인정 세션의 날짜별 이력에서 매번 계산하므로 규칙이 바뀌어도 마이그레이션이 필요 없다
data class UserLevel(
    val level: Int,
    // 현재 레벨 안에서 쌓인 XP
    val currentXp: Int,
    // 현재 레벨에서 다음 레벨까지 필요한 전체 XP
    val xpForNextLevel: Int,
    val totalXp: Int,
) {
    companion object {
        const val BASE_SESSION_XP = 100
        private const val FIRST_LEVEL_XP = 1000
        private const val LEVEL_GROWTH = 1.05

        // 하루에 XP로 인정하는 최대 세션 수 (세션을 여러 개 등록해 XP를 불리는 것을 막는다)
        private const val MAX_SESSIONS_PER_DAY = 2

        // 연속 운동일 판정: 운동일 사이 간격이 이 일수 이내면 연속(월수금/주 5회 분할도 이어지게 연속 이틀 휴식까지 허용)
        private const val MAX_GAP_DAYS = 3L

        // XP 인정 기준일. 운동 통계와 같은 기준일(WorkoutRecordPolicy.STRICT_FROM)을 쓴다
        val STRICT_QUALIFICATION_FROM: LocalDate = WorkoutRecordPolicy.STRICT_FROM

        // 부분 수행 세션(완료 인정 조건은 못 채웠지만 기록된 수행량이 기준 이상)의 고정 XP. 연속 보너스는 없다
        const val PARTIAL_SESSION_XP = 50

        // 부분 수행 XP 인정 시작일(세션 date). 이전 세션은 소급하지 않는다
        val PARTIAL_QUALIFICATION_FROM: LocalDate = LocalDate.of(2026, 10, 3)

        // 부분 수행 기준: 근력은 기록된 세트 수 합, 유산소는 기록된 수행 시간(초) 합
        const val PARTIAL_MIN_SETS = 2
        const val PARTIAL_MIN_CARDIO_SECONDS = 20 * 60

        // 연속 N번째 운동일부터 XP 배수 적용
        private const val STREAK_BONUS_FROM = 3
        private const val STREAK_MULTIPLIER = 1.2

        // 날짜별 정상 인정 세션 수와 부분 수행 세션 수(날짜 -> 세션 수)로 누적 XP를 계산해 레벨 정보를 만든다
        fun fromSessionCounts(
            sessionCountsByDate: Map<LocalDate, Int>,
            partialCountsByDate: Map<LocalDate, Int> = emptyMap(),
        ): UserLevel = ofTotalXp(totalXp(sessionCountsByDate, partialCountsByDate))

        // 누적 XP로 현재 레벨/레벨 내 XP/다음 레벨 필요 XP를 계산 (레벨 1에서 시작해 레벨별 필요량을 차례로 차감)
        fun ofTotalXp(totalXp: Int): UserLevel {
            var level = 1
            var remaining = totalXp
            var need = xpRequiredFor(level)
            while (remaining >= need) {
                remaining -= need
                level++
                need = xpRequiredFor(level)
            }
            return UserLevel(level = level, currentXp = remaining, xpForNextLevel = need, totalXp = totalXp)
        }

        // 해당 레벨에서 다음 레벨로 가는 데 필요한 XP (1레벨 1000, 이후 레벨마다 5% 증가, 반올림)
        fun xpRequiredFor(level: Int): Int = (FIRST_LEVEL_XP * LEVEL_GROWTH.pow(level - 1)).roundToInt()

        // 날짜 오름차순으로 세션별 XP를 합산한다. 연속 운동일은 정상 인정 세션이 있는 날만 이어지고(연속 3번째부터 배수),
        // 부분 수행은 50 XP 고정이며 연속에 포함되지 않는다. 하루 한도(2세션)는 정상/부분을 합쳐 XP가 큰 것부터 채운다
        private fun totalXp(fullCountsByDate: Map<LocalDate, Int>, partialCountsByDate: Map<LocalDate, Int>): Int {
            var total = 0
            var streak = 0
            var previousFullDate: LocalDate? = null
            val dates = (fullCountsByDate.filterValues { it > 0 }.keys + partialCountsByDate.filterValues { it > 0 }.keys).toSortedSet()
            for (date in dates) {
                val fullCount = fullCountsByDate[date] ?: 0
                val partialCount = partialCountsByDate[date] ?: 0
                var fullXp = BASE_SESSION_XP
                if (fullCount > 0) {
                    streak = if (previousFullDate != null && ChronoUnit.DAYS.between(previousFullDate, date) <= MAX_GAP_DAYS) streak + 1 else 1
                    if (streak >= STREAK_BONUS_FROM) fullXp = (BASE_SESSION_XP * STREAK_MULTIPLIER).roundToInt()
                    previousFullDate = date
                }
                val dayXps = List(fullCount) { fullXp } + List(partialCount) { PARTIAL_SESSION_XP }
                total += dayXps.sortedDescending().take(MAX_SESSIONS_PER_DAY).sum()
            }
            return total
        }
    }
}
