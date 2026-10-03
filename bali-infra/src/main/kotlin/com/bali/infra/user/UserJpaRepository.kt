package com.bali.infra.user

import com.bali.core.plan.Plan
import com.bali.core.user.AuthProvider
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

// UserJpaEntity를 위한 Spring Data JPA 리포지토리 인터페이스.
interface UserJpaRepository : JpaRepository<UserJpaEntity, UUID> {
    // 프로바이더와 프로바이더별 ID로 사용자를 조회.
    fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): UserJpaEntity?

    // 이메일로 사용자를 조회 (이메일 중복 검증용)
    fun findByEmail(email: String): UserJpaEntity?

    // 특정 상태의 사용자 전체 목록을 조회 (Spring Data가 메서드명으로 쿼리 자동 생성)
    fun findAllByStatus(status: com.bali.core.user.UserStatus): List<UserJpaEntity>

    // 플랜과 루틴 수/직접 만든 운동 수를 서브쿼리로 묶어 한 번의 DB 왕복으로 조회
    @Query(
        """
        SELECT u.plan AS plan, u.planExpiresAt AS planExpiresAt,
            (SELECT COUNT(t) FROM WorkoutTemplateJpaEntity t WHERE t.userId = u.id AND t.deleted = false) AS templateCount,
            (SELECT COUNT(e) FROM ExerciseJpaEntity e WHERE e.scope = 'PERSONAL' AND e.ownerId = u.id AND e.deleted = false) AS personalExerciseCount
        FROM UserJpaEntity u WHERE u.id = :userId
        """
    )
    fun findPlanUsage(@Param("userId") userId: UUID): PlanUsageProjection?
}

// findPlanUsage 쿼리의 결과 행
interface PlanUsageProjection {
    val plan: Plan
    val planExpiresAt: Instant?
    val templateCount: Long
    val personalExerciseCount: Long
}
