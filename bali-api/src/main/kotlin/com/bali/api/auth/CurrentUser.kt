package com.bali.api.auth

import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

// SecurityContextHolder에서 현재 인증된 사용자의 UUID를 추출
fun currentUserId(): UUID =
    UUID.fromString(SecurityContextHolder.getContext().authentication.name)
