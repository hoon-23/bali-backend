package com.bali.batch

import com.bali.core.session.SessionStatus
import com.bali.core.session.WorkoutSession
import com.bali.core.session.WorkoutSessionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@SpringBootTest(classes = [BaliBatchApplication::class])
@Transactional
class AbandonStaleSessionsRunnerTest {

    @Autowired lateinit var runner: AbandonStaleSessionsRunner
    @Autowired lateinit var sessionRepository: WorkoutSessionRepository

    // 지정 날짜/상태의 빈 세션을 저장하고 id 반환
    private fun saveSession(date: LocalDate, status: SessionStatus): UUID =
        sessionRepository.save(WorkoutSession(id = null, userId = UUID.randomUUID(), date = date, templateId = null, status = status, logs = emptyList())).id!!

    // KST 기준 어제까지의 IN_PROGRESS는 전환하고 오늘 날짜 IN_PROGRESS는 유지하는지 확인
    @Test
    fun `어제 IN_PROGRESS는 ABANDONED로 전환되고 오늘 IN_PROGRESS는 유지된다`() {
        val today = LocalDate.of(2026, 10, 1)
        val yesterday = saveSession(today.minusDays(1), SessionStatus.IN_PROGRESS)
        val todays = saveSession(today, SessionStatus.IN_PROGRESS)

        val exitCode = runner.run(today)

        assertEquals(0, exitCode)
        assertEquals(SessionStatus.ABANDONED, sessionRepository.findById(yesterday)?.status)
        assertEquals(SessionStatus.IN_PROGRESS, sessionRepository.findById(todays)?.status)
    }

    // 재실행해도 결과가 같은지(멱등) 확인
    @Test
    fun `재실행해도 이미 전환된 세션은 그대로다`() {
        val today = LocalDate.of(2026, 10, 1)
        val stale = saveSession(today.minusDays(3), SessionStatus.IN_PROGRESS)

        runner.run(today)
        runner.run(today)

        assertEquals(SessionStatus.ABANDONED, sessionRepository.findById(stale)?.status)
    }

    // UTC로는 아직 일요일인 월요일 00:05 KST 실행에서 일요일(KST 어제) 세션이 전환되는지 확인
    @Test
    fun `KST 월요일 새벽 실행은 UTC가 일요일이어도 KST 일요일 세션을 전환한다`() {
        val today = AnalysisPeriod.todayInApp(Instant.parse("2026-09-27T15:05:00Z"))
        val sunday = saveSession(LocalDate.of(2026, 9, 27), SessionStatus.IN_PROGRESS)
        val monday = saveSession(LocalDate.of(2026, 9, 28), SessionStatus.IN_PROGRESS)

        runner.run(today)

        assertEquals(LocalDate.of(2026, 9, 28), today)
        assertEquals(SessionStatus.ABANDONED, sessionRepository.findById(sunday)?.status)
        assertEquals(SessionStatus.IN_PROGRESS, sessionRepository.findById(monday)?.status)
    }
}
