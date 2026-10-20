package com.bali.infra.auth

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

// AppleRefreshTokenJpaEntity를 위한 Spring Data JPA 리포지토리 인터페이스
interface AppleRefreshTokenJpaRepository : JpaRepository<AppleRefreshTokenJpaEntity, UUID>
