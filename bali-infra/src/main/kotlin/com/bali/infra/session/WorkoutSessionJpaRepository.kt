package com.bali.infra.session

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

    // 세션을 삭제. bulk delete는 영속성 컨텍스트를 갱신하지 않아 DB cascade로 사라진
    // session_logs가 캐시에 남을 수 있으므로 clearAutomatically로 1차 캐시를 비운다
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM WorkoutSessionJpaEntity s WHERE s.id = :id")
    fun deleteSessionById(@Param("id") id: UUID)
}
