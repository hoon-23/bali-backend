package com.bali.batch

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

// 주간/월간 분석 배치가 집계할 "직전 완료 기간"을 앱 기준 시간대(KST)로 계산하는 순수 함수 모음
object AnalysisPeriod {

    val APP_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

    // 컨테이너 시간대(UTC)와 무관하게 KST 기준 오늘 날짜를 반환 (월요일 00:00 KST는 UTC로는 아직 일요일)
    fun todayInApp(now: Instant = Instant.now()): LocalDate = now.atZone(APP_ZONE).toLocalDate()

    // today가 속한 주의 바로 직전 완료 주의 월요일
    fun lastCompletedWeekOf(today: LocalDate): LocalDate =
        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)

    // today가 속한 달의 바로 직전 완료 달의 1일
    fun lastCompletedMonthOf(today: LocalDate): LocalDate = today.withDayOfMonth(1).minusMonths(1)
}
