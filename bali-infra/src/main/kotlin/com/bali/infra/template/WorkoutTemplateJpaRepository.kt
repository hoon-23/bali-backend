package com.bali.infra.template

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface WorkoutTemplateJpaRepository : JpaRepository<WorkoutTemplateJpaEntity, UUID> {
    // 특정 유저의 소프트 삭제되지 않은 템플릿 목록
    @Query("SELECT t FROM WorkoutTemplateJpaEntity t WHERE t.userId = :userId AND t.deleted = false")
    fun findAllByUserIdAndDeletedFalse(@Param("userId") userId: UUID): List<WorkoutTemplateJpaEntity>
}
