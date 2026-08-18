package com.bali.api.user

import com.bali.api.auth.jwt.JwtTokenProvider
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
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

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var sessionJpaRepository: com.bali.infra.session.WorkoutSessionJpaRepository

    @Autowired
    lateinit var sessionLogJpaRepository: com.bali.infra.session.SessionLogJpaRepository

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

    // DELETE /api/v1/users/me 호출시 email/providerId/nickname이 파기(익명화)되는지 확인 (PIPA 파기 의무)
    @Test
    fun `DELETE me 호출하면 email providerId nickname이 파기된다`() {
        val entity = userJpaRepository.save(
            com.bali.infra.user.UserJpaEntity(
                email = "withdraw2@example.com",
                provider = com.bali.core.user.AuthProvider.GOOGLE,
                providerId = "sub-withdraw2",
                nickname = "탈퇴전닉네임",
            )
        )
        val token = jwtTokenProvider.generateToken(entity.id, entity.email)

        mockMvc.perform(delete("/api/v1/users/me").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)

        val reloaded = userJpaRepository.findById(entity.id).orElseThrow()
        org.junit.jupiter.api.Assertions.assertEquals("withdrawn-${entity.id}@bali.internal", reloaded.email)
        org.junit.jupiter.api.Assertions.assertEquals("withdrawn-${entity.id}", reloaded.providerId)
        org.junit.jupiter.api.Assertions.assertEquals("탈퇴한사용자", reloaded.nickname)
    }

    // GET /api/v1/users/me 응답에 nickname/weeklyGoalSessions/consecutiveDays가 포함되는지 확인
    @Test
    fun `GET me 응답에 nickname weeklyGoalSessions consecutiveDays가 포함된다`() {
        val entity = userJpaRepository.save(
            com.bali.infra.user.UserJpaEntity(
                email = "profile@example.com",
                nickname = "행복한옥수수07",
                weeklyGoalSessions = 5,
                provider = com.bali.core.user.AuthProvider.GOOGLE,
                providerId = "sub-profile",
            )
        )
        val today = java.time.LocalDate.now()
        val session = sessionJpaRepository.save(
            com.bali.infra.session.WorkoutSessionJpaEntity(userId = entity.id, date = today)
        )
        sessionLogJpaRepository.save(
            com.bali.infra.session.SessionLogJpaEntity(
                sessionId = session.id, exerciseId = UUID.randomUUID(), completed = true,
                actualSets = 3, actualReps = 10, actualWeight = java.math.BigDecimal("60.0"),
            )
        )
        val token = jwtTokenProvider.generateToken(entity.id, entity.email)

        val response = mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andReturn().response
        // MockHttpServletResponse.contentAsString은 Content-Type에 charset이 없으면 ISO-8859-1로
        // 디코딩해 한글이 깨지므로, 응답 바이트를 UTF-8로 직접 디코딩한다
        val json = objectMapper.readTree(String(response.contentAsByteArray, Charsets.UTF_8))
        org.junit.jupiter.api.Assertions.assertEquals("행복한옥수수07", json.get("nickname").asText())
        org.junit.jupiter.api.Assertions.assertEquals(5, json.get("weeklyGoalSessions").asInt())
        org.junit.jupiter.api.Assertions.assertEquals(1, json.get("consecutiveDays").asInt())
    }

    // PATCH /api/v1/users/me로 nickname만 바꾸면 weeklyGoalSessions는 유지되는지 확인 (부분 업데이트)
    @Test
    fun `PATCH me로 nickname만 바꾸면 weeklyGoalSessions는 유지된다`() {
        val entity = userJpaRepository.save(
            com.bali.infra.user.UserJpaEntity(
                email = "patch@example.com",
                nickname = "원래닉네임",
                weeklyGoalSessions = 4,
                provider = com.bali.core.user.AuthProvider.GOOGLE,
                providerId = "sub-patch",
            )
        )
        val token = jwtTokenProvider.generateToken(entity.id, entity.email)

        val response = mockMvc.perform(
            patch("/api/v1/users/me").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nickname":"새닉네임"}""")
        ).andExpect(status().isOk).andReturn().response
        // MockHttpServletResponse.contentAsString은 Content-Type에 charset이 없으면 ISO-8859-1로
        // 디코딩해 한글이 깨지므로, 응답 바이트를 UTF-8로 직접 디코딩한다
        val json = objectMapper.readTree(String(response.contentAsByteArray, Charsets.UTF_8))
        org.junit.jupiter.api.Assertions.assertEquals("새닉네임", json.get("nickname").asText())
        org.junit.jupiter.api.Assertions.assertEquals(4, json.get("weeklyGoalSessions").asInt())
    }

    // PATCH /api/v1/users/me에 범위 밖 weeklyGoalSessions를 주면 400이 반환되는지 확인
    @Test
    fun `PATCH me로 weeklyGoalSessions 범위 밖 값을 주면 400 반환`() {
        val entity = userJpaRepository.save(
            com.bali.infra.user.UserJpaEntity(
                email = "invalid@example.com",
                nickname = "닉네임",
                provider = com.bali.core.user.AuthProvider.GOOGLE,
                providerId = "sub-invalid",
            )
        )
        val token = jwtTokenProvider.generateToken(entity.id, entity.email)

        mockMvc.perform(
            patch("/api/v1/users/me").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"weeklyGoalSessions":8}""")
        ).andExpect(status().isBadRequest)
    }

    // PATCH /api/v1/users/me에 공백 nickname을 주면 400이 반환되는지 확인
    @Test
    fun `PATCH me로 공백 nickname을 주면 400 반환`() {
        val entity = userJpaRepository.save(
            com.bali.infra.user.UserJpaEntity(
                email = "blank@example.com",
                nickname = "닉네임",
                provider = com.bali.core.user.AuthProvider.GOOGLE,
                providerId = "sub-blank",
            )
        )
        val token = jwtTokenProvider.generateToken(entity.id, entity.email)

        mockMvc.perform(
            patch("/api/v1/users/me").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nickname":"   "}""")
        ).andExpect(status().isBadRequest)
    }
}
