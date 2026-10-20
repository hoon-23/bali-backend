package com.bali.infra.auth

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

// RefreshTokenJpaEntity를 위한 Spring Data JPA 리포지토리 인터페이스.
interface RefreshTokenJpaRepository : JpaRepository<RefreshTokenJpaEntity, UUID> {
    // 토큰 해시로 조회.
    fun findByTokenHash(tokenHash: String): RefreshTokenJpaEntity?

    // revoked = false인 경우에만 원자적으로 true로 바꾼다 (조건부 UPDATE). find 후 save하는
    // 방식은 두 트랜잭션이 동시에 같은 row를 읽고 각자 폐기 처리에 성공해버릴 수 있어(둘 다
    // 새 토큰을 발급하게 됨), WHERE 절에 revoked = false를 넣어 DB의 row lock으로 단
    // 하나의 트랜잭션만 성공하도록 강제한다. clearAutomatically로 영속성 컨텍스트 1차 캐시를
    // 비워 이후 조회가 갱신된 값을 보게 한다
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RefreshTokenJpaEntity t SET t.revoked = true WHERE t.id = :id AND t.revoked = false")
    fun revokeIfActive(@Param("id") id: UUID): Int

    // 특정 유저의 미폐기 토큰을 한 번에 폐기한다 (회원 탈퇴용). 같은 트랜잭션의 미반영 변경(사용자 WITHDRAWN 처리)이
    // clearAutomatically로 버려지지 않도록 실행 전에 flush한다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshTokenJpaEntity t SET t.revoked = true WHERE t.userId = :userId AND t.revoked = false")
    fun revokeAllByUserId(@Param("userId") userId: UUID): Int
}
