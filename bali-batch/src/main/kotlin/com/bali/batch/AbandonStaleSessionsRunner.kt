package com.bali.batch

import com.bali.core.session.WorkoutSessionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate

// 매일 자정(KST) 직후 실행되어, 오늘(KST)보다 이전 날짜인 IN_PROGRESS 세션을 ABANDONED(중단)로 확정한다.
// 이미 전환된 세션은 대상이 아니라 재실행해도 안전하다(멱등). 세트 기록은 건드리지 않는다
@Component
class AbandonStaleSessionsRunner(
    private val sessionRepository: WorkoutSessionRepository,
) {
    private val log = LoggerFactory.getLogger(AbandonStaleSessionsRunner::class.java)

    // 실행 진입점. 전환 건수를 로그로 남기고 성공 시 0을 반환 (today는 테스트에서 KST 경계를 재현하려고 주입 가능)
    fun run(today: LocalDate = AnalysisPeriod.todayInApp()): Int {
        val abandoned = sessionRepository.abandonInProgressBefore(today)
        log.info("중단 처리된 세션 수: {} (기준일 {})", abandoned, today)
        return 0
    }
}
