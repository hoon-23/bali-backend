package com.bali.api.user

import com.bali.api.auth.jwt.JwtTokenProvider
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

// 사용자 조회 및 탈퇴 API 엔드포인트의 동작을 검증하는 통합 테스트
@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.transaction.annotation.Transactional
class UserControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jwtTokenProvider: JwtTokenProvider

    @Autowired
    lateinit var userJpaRepository: com.bali.infra.user.UserJpaRepository

    // 인증 토큰 없이 GET /api/v1/users/me 호출시 401 Unauthorized를 반환하는지 확인
    @Test
    fun `토큰 없이 GET me 호출하면 401 반환`() {
        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized)
    }

    // 유효한 JWT 토큰으로 GET /api/v1/users/me 호출시 200 OK와 사용자 정보를 반환하는지 확인
    @Test
    fun `유효한 토큰으로 GET me 호출하면 사용자 정보 반환`() {
        val entity = userJpaRepository.save(
            com.bali.infra.user.UserJpaEntity(
                email = "me@example.com",
                provider = com.bali.core.user.AuthProvider.GOOGLE,
                providerId = "sub-me",
            )
        )
        val token = jwtTokenProvider.generateToken(entity.id, entity.email)

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
    }

    // DELETE /api/v1/users/me 호출시 사용자 상태를 WITHDRAWN으로 변경하고 204 No Content를 반환하는지 확인
    @Test
    fun `DELETE me 호출하면 사용자 상태가 WITHDRAWN으로 변경되고 204 반환`() {
        val entity = userJpaRepository.save(
            com.bali.infra.user.UserJpaEntity(
                email = "withdraw@example.com",
                provider = com.bali.core.user.AuthProvider.GOOGLE,
                providerId = "sub-withdraw",
            )
        )
        val token = jwtTokenProvider.generateToken(entity.id, entity.email)

        mockMvc.perform(delete("/api/v1/users/me").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)

        val reloaded = userJpaRepository.findById(entity.id).orElseThrow()
        org.junit.jupiter.api.Assertions.assertEquals(
            com.bali.core.user.UserStatus.WITHDRAWN,
            reloaded.status,
        )
    }
}
