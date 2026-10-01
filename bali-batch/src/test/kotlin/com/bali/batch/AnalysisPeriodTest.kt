package com.bali.batch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class AnalysisPeriodTest {

    // 월요일 00:02 KST(= 일요일 15:02 UTC)에도 오늘이 월요일(KST)로 계산되는지 확인
    @Test
    fun `UTC 일요일 오후는 KST 월요일로 변환된다`() {
        val today = AnalysisPeriod.todayInApp(Instant.parse("2026-09-27T15:02:00Z"))

        assertEquals(LocalDate.of(2026, 9, 28), today)
    }

    // 월요일 00:02 KST 실행 시 직전 완료 주가 그 전 월요일(9/21)이어야 한다 (UTC 기준이면 9/14로 어긋남)
    @Test
    fun `월요일 새벽 배치의 직전 완료 주는 지난 월요일이다`() {
        val today = AnalysisPeriod.todayInApp(Instant.parse("2026-09-27T15:02:00Z"))

        assertEquals(LocalDate.of(2026, 9, 21), AnalysisPeriod.lastCompletedWeekOf(today))
    }

    // 주중 어느 날이든 직전 완료 주는 그 주 월요일보다 한 주 앞이다
    @Test
    fun `수요일에도 직전 완료 주는 지난 월요일이다`() {
        assertEquals(LocalDate.of(2026, 9, 21), AnalysisPeriod.lastCompletedWeekOf(LocalDate.of(2026, 9, 30)))
    }

    // 1일 00:01 KST(= 전날 15:01 UTC) 실행 시 직전 완료 달이 9월이어야 한다 (UTC 기준이면 8월로 어긋남)
    @Test
    fun `월초 새벽 배치의 직전 완료 달은 지난달이다`() {
        val today = AnalysisPeriod.todayInApp(Instant.parse("2026-09-30T15:01:00Z"))

        assertEquals(LocalDate.of(2026, 10, 1), today)
        assertEquals(LocalDate.of(2026, 9, 1), AnalysisPeriod.lastCompletedMonthOf(today))
    }

    // 연초에는 직전 완료 달이 전년도 12월이다
    @Test
    fun `1월 1일의 직전 완료 달은 전년도 12월이다`() {
        assertEquals(LocalDate.of(2025, 12, 1), AnalysisPeriod.lastCompletedMonthOf(LocalDate.of(2026, 1, 1)))
    }
}
