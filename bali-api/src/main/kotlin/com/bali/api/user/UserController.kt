package com.bali.api.user

import com.bali.core.user.UserRepository
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

// 사용자 관련 API 엔드포인트를 처리하는 REST 컨트롤러
@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userRepository: UserRepository,
) {

    // 인증된 사용자의 정보를 조회하는 엔드포인트
    @GetMapping("/me")
    fun getMe(): ResponseEntity<UserResponse> {
        val user = userRepository.findById(currentUserId())
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(UserResponse.from(user))
    }

    // 인증된 사용자의 계정을 탈퇴 처리하는 엔드포인트
    @DeleteMapping("/me")
    fun withdraw(): ResponseEntity<Void> {
        val user = userRepository.findById(currentUserId())
            ?: return ResponseEntity.notFound().build()
        // 사용자 상태를 WITHDRAWN으로 변경하고 저장
        userRepository.save(user.withdraw())
        return ResponseEntity.noContent().build()
    }

    // SecurityContextHolder에서 현재 인증된 사용자의 UUID를 추출
    private fun currentUserId(): UUID =
        UUID.fromString(SecurityContextHolder.getContext().authentication.name)
}
