package com.bali.infra.session

import com.bali.core.session.SessionLog
import com.bali.core.session.WorkoutSession
import com.bali.core.session.WorkoutSessionRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

// JPA 백엔드로 WorkoutSessionRepository 포트를 구현하는 Spring 리포지토리 어댑터
@Repository
class WorkoutSessionRepositoryAdapter(
    private val sessionJpaRepository: WorkoutSessionJpaRepository,
    private val logJpaRepository: SessionLogJpaRepository,
) : WorkoutSessionRepository {

    // 고유 식별자로 세션과 그 logs를 함께 조회
    @Transactional
    override fun findById(id: UUID): WorkoutSession? {
        val entity = sessionJpaRepository.findById(id).orElse(null) ?: return null
        return entity.toDomain(logJpaRepository.findBySessionIdOrderBySortOrderAsc(id))
    }

    // 특정 유저의 기간 내 세션 목록을 logs와 함께 조회 (logs는 배치 조회로 N+1 방지)
    @Transactional
    override fun findAllByUserId(userId: UUID, from: LocalDate, to: LocalDate): List<WorkoutSession> {
        val sessions = sessionJpaRepository.findAllByUserIdAndDateBetween(userId, from, to)
        // findBySessionIdInOrderBySortOrderAsc가 sortOrder 오름차순으로 반환하고,
        // Kotlin의 groupBy는 원본 순서(encounter order)를 그룹 내에 그대로 보존하므로
        // 그룹핑 이후에도 세션별 logs 리스트는 sortOrder 순서를 유지한다.
        val logsBySessionId = logJpaRepository.findBySessionIdInOrderBySortOrderAsc(sessions.map { it.id }).groupBy { it.sessionId }
        return sessions.map { it.toDomain(logsBySessionId[it.id] ?: emptyList()) }
    }

    // 세션을 저장하고 초기 logs를 함께 삽입
    @Transactional
    override fun save(session: WorkoutSession): WorkoutSession {
        val sessionId = session.id ?: UUID.randomUUID()
        sessionJpaRepository.save(
            WorkoutSessionJpaEntity(id = sessionId, userId = session.userId, date = session.date, templateId = session.templateId)
        )
        val savedLogs = session.logs.map { logJpaRepository.save(it.toEntity(sessionId)) }
        return session.copy(id = sessionId, logs = savedLogs.map { it.toDomain() })
    }

    // 고유 식별자로 단일 log를 조회
    @Transactional
    override fun findLogById(logId: UUID): SessionLog? =
        logJpaRepository.findById(logId).orElse(null)?.toDomain()

    // 세션에 새 log를 추가
    @Transactional
    override fun addLogs(sessionId: UUID, logs: List<SessionLog>): List<SessionLog> =
        logs.map { logJpaRepository.save(it.toEntity(sessionId)).toDomain() }

    // 기존 log를 exerciseId/target*/sortOrder 기준으로 전체 교체 (id가 일치하는 row를 upsert)
    @Transactional
    override fun updateLogs(sessionId: UUID, logs: List<SessionLog>) {
        logs.forEach { logJpaRepository.save(it.toEntity(sessionId)) }
    }

    // 지정한 logId들을 세션에서 제거
    @Transactional
    override fun removeLogs(sessionId: UUID, logIds: List<UUID>) {
        logJpaRepository.deleteByIdIn(logIds)
    }

    // 단일 log의 실제 수행값/완료 상태를 기록 (null인 필드는 변경하지 않음)
    @Transactional
    override fun recordActual(
        logId: UUID,
        completed: Boolean?,
        actualSets: Int?, actualReps: Int?, actualWeight: BigDecimal?,
        actualDurationSeconds: Int?, actualPace: String?,
    ): SessionLog? {
        val entity = logJpaRepository.findById(logId).orElse(null) ?: return null
        completed?.let { entity.completed = it }
        actualSets?.let { entity.actualSets = it }
        actualReps?.let { entity.actualReps = it }
        actualWeight?.let { entity.actualWeight = it }
        actualDurationSeconds?.let { entity.actualDurationSeconds = it }
        actualPace?.let { entity.actualPace = it }
        return logJpaRepository.save(entity).toDomain()
    }

    // 세션을 삭제. session_logs는 DB cascade(ON DELETE CASCADE)로 함께 제거된다
    @Transactional
    override fun deleteById(id: UUID) {
        sessionJpaRepository.deleteSessionById(id)
    }

    // 완료된 로그가 있는 날짜 집합을 조회
    override fun findActiveDates(userId: UUID, since: LocalDate): Set<LocalDate> =
        sessionJpaRepository.findActiveDates(userId, since).toSet()

    // JPA 엔티티(+logs)를 도메인 모델로 변환
    private fun WorkoutSessionJpaEntity.toDomain(logs: List<SessionLogJpaEntity>) = WorkoutSession(
        id = id, userId = userId, date = date, templateId = templateId, logs = logs.map { it.toDomain() },
    )

    // SessionLog 도메인 모델을 JPA 엔티티로 변환
    private fun SessionLog.toEntity(sessionId: UUID) = SessionLogJpaEntity(
        id = id ?: UUID.randomUUID(), sessionId = sessionId, exerciseId = exerciseId, sortOrder = sortOrder,
        completed = completed, targetSets = targetSets, targetReps = targetReps, targetWeight = targetWeight,
        targetDurationSeconds = targetDurationSeconds, targetPace = targetPace,
        actualSets = actualSets, actualReps = actualReps, actualWeight = actualWeight,
        actualDurationSeconds = actualDurationSeconds, actualPace = actualPace,
    )

    // SessionLog JPA 엔티티를 도메인 모델로 변환
    private fun SessionLogJpaEntity.toDomain() = SessionLog(
        id = id, exerciseId = exerciseId, sortOrder = sortOrder, completed = completed,
        targetSets = targetSets, targetReps = targetReps, targetWeight = targetWeight,
        targetDurationSeconds = targetDurationSeconds, targetPace = targetPace,
        actualSets = actualSets, actualReps = actualReps, actualWeight = actualWeight,
        actualDurationSeconds = actualDurationSeconds, actualPace = actualPace,
    )
}
