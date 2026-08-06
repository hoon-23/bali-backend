package com.bali.infra.session

import org.springframework.data.jpa.repository.JpaRepository
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
}
