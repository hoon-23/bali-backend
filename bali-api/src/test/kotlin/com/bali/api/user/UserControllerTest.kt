package com.bali.api.user

import com.bali.api.auth.JwtTokenProvider
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

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

    @Test
    fun `토큰 없이 GET me 호출하면 401 반환`() {
        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized)
    }

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
