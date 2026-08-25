package com.bali.core.session

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

// 운동 세션 영속성을 위한 포트 인터페이스
interface WorkoutSessionRepository {
    // 고유 식별자로 세션을 조회
    fun findById(id: UUID): WorkoutSession?

    // 특정 유저의 기간 내(from~to, inclusive) 세션 목록 조회
    fun findAllByUserId(userId: UUID, from: LocalDate, to: LocalDate): List<WorkoutSession>

    // 세션을 저장 (신규 생성 시 초기 logs를 함께 삽입)
    fun save(session: WorkoutSession): WorkoutSession

    // 고유 식별자로 단일 log를 조회
    fun findLogById(logId: UUID): SessionLog?

    // 세션에 새 log를 추가 (즉흥 추가 종목)
    fun addLogs(sessionId: UUID, logs: List<SessionLog>): List<SessionLog>

    // 기존 log를 exerciseId/target*/sortOrder 기준으로 전체 교체 (actual*/completed는 별도 유지)
    fun updateLogs(sessionId: UUID, logs: List<SessionLog>)

    // 지정한 logId들을 세션에서 제거
    fun removeLogs(sessionId: UUID, logIds: List<UUID>)

    // 단일 log의 실제 수행값/완료 상태를 기록 (null인 필드는 변경하지 않음, setTimings는 non-null이면 전체 교체)
    fun recordActual(
        logId: UUID,
        completed: Boolean?,
        actualSets: Int?, actualReps: Int?, actualWeight: BigDecimal?,
        actualDurationSeconds: Int?, actualPace: String?,
        setTimings: List<SetTiming>?,
    ): SessionLog?

    // 세션을 삭제 (session_logs는 DB의 ON DELETE CASCADE로 함께 제거됨)
    fun deleteById(id: UUID)

    // 세션의 status만 갱신 (없으면 null)
    fun updateStatus(sessionId: UUID, status: SessionStatus): WorkoutSession?

    // 세션의 perceivedDifficulty만 갱신 (없으면 null)
    fun updatePerceivedDifficulty(sessionId: UUID, perceivedDifficulty: Int): WorkoutSession?

    // 완료된 로그가 하나 이상 있는 날짜 집합을 조회 (연속운동일 계산용, since 이후만)
    fun findActiveDates(userId: UUID, since: LocalDate): Set<LocalDate>

    // 특정 날짜/상태의 세션 전체를 유저 무관하게 조회 (배치의 리마인더 대상 조회용)
    fun findAllByDateAndStatus(date: LocalDate, status: SessionStatus): List<WorkoutSession>

    // 완료된 로그가 있는 가장 최근 날짜 (없으면 null). 이탈 알림 판정에 사용
    fun findLastActiveDate(userId: UUID): LocalDate?
}
