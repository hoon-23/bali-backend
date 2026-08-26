package com.bali.infra.session

import com.bali.core.session.SessionStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.util.UUID

interface WorkoutSessionJpaRepository : JpaRepository<WorkoutSessionJpaEntity, UUID> {
    // 특정 유저의 기간 내(from~to, inclusive) 세션 목록
    @Query("SELECT s FROM WorkoutSessionJpaEntity s WHERE s.userId = :userId AND s.date BETWEEN :from AND :to")
    fun findAllByUserIdAndDateBetween(
        @Param("userId") userId: UUID,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
    ): List<WorkoutSessionJpaEntity>

    // 커서(date, id) 이전 세션을 date DESC, id DESC로 조회 (동일 date가 여러 건이라 id를 2차 정렬 기준으로 써야 커서가 안정적).
    // cursorDate가 null이면 커서 조건을 건너뛰어 최신부터 시작. Pageable로 개수(limit)만 제어하고 페이지 offset은 항상 0을 쓴다(오프셋 방식이 아니라 커서로 위치를 지정하므로)
    @Query("""
        SELECT s FROM WorkoutSessionJpaEntity s
        WHERE s.userId = :userId
          AND (CAST(:from AS date) IS NULL OR s.date >= :from)
          AND (CAST(:to AS date) IS NULL OR s.date <= :to)
          AND (
            CAST(:cursorDate AS date) IS NULL
            OR s.date < :cursorDate
            OR (s.date = :cursorDate AND s.id < CAST(:cursorId AS uuid))
          )
        ORDER BY s.date DESC, s.id DESC
    """)
    fun findPageByUserId(
        @Param("userId") userId: UUID,
        @Param("from") from: LocalDate?,
        @Param("to") to: LocalDate?,
        @Param("cursorDate") cursorDate: LocalDate?,
        @Param("cursorId") cursorId: UUID?,
        pageable: Pageable,
    ): List<WorkoutSessionJpaEntity>

    // 세션을 삭제. bulk delete는 영속성 컨텍스트를 갱신하지 않아 DB cascade로 사라진
    // session_logs가 캐시에 남을 수 있으므로 clearAutomatically로 1차 캐시를 비운다
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM WorkoutSessionJpaEntity s WHERE s.id = :id")
    fun deleteSessionById(@Param("id") id: UUID)

    // 완료된 로그가 있는 날짜 집합을 조회. WorkoutSessionJpaEntity와 SessionLogJpaEntity는
    // 객체 그래프 관계가 없어(순수 FK 컬럼) sessionId=id 조건의 명시적 JOIN ON을 쓴다
    @Query("""
        SELECT DISTINCT s.date FROM WorkoutSessionJpaEntity s
        JOIN SessionLogJpaEntity l ON l.sessionId = s.id
        WHERE s.userId = :userId AND s.date >= :since AND l.completed = true
    """)
    fun findActiveDates(@Param("userId") userId: UUID, @Param("since") since: LocalDate): List<LocalDate>

    // 특정 날짜/상태의 세션 전체 (유저 무관)
    @Query("SELECT s FROM WorkoutSessionJpaEntity s WHERE s.date = :date AND s.status = :status")
    fun findAllByDateAndStatus(@Param("date") date: LocalDate, @Param("status") status: SessionStatus): List<WorkoutSessionJpaEntity>

    // 완료된 로그가 있는 가장 최근 날짜
    @Query("""
        SELECT MAX(s.date) FROM WorkoutSessionJpaEntity s
        JOIN SessionLogJpaEntity l ON l.sessionId = s.id
        WHERE s.userId = :userId AND l.completed = true
    """)
    fun findLastActiveDate(@Param("userId") userId: UUID): LocalDate?
}
