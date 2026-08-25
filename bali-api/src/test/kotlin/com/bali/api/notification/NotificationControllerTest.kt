package com.bali.api.notification

import com.bali.api.auth.jwt.JwtTokenProvider
import com.bali.core.notification.DeviceTokenRepository
import com.bali.core.notification.NotificationSettingsRepository
import com.bali.core.user.AuthProvider
import com.bali.infra.user.UserJpaEntity
import com.bali.infra.user.UserJpaRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired lateinit var userJpaRepository: UserJpaRepository
    @Autowired lateinit var deviceTokenRepository: DeviceTokenRepository
    @Autowired lateinit var settingsRepository: NotificationSettingsRepository

    private fun issueTokenForNewUser(): Pair<String, UUID> {
        val entity = userJpaRepository.save(
            UserJpaEntity(email = "notif-test-${System.nanoTime()}@example.com", provider = AuthProvider.GOOGLE, providerId = "sub-notif-${System.nanoTime()}")
        )
        return jwtTokenProvider.generateToken(entity.id, entity.email) to entity.id
    }

    @Test
    fun `POST device-token 호출하면 토큰을 등록한다`() {
        val (token, userId) = issueTokenForNewUser()
        val body = """{"token":"ExponentPushToken[api-1]","platform":"IOS"}"""

        mockMvc.perform(post("/api/v1/notifications/device-token").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isNoContent)

        assertEquals(1, deviceTokenRepository.findAllByUserId(userId).size)
    }

    @Test
    fun `DELETE device-token 호출하면 토큰을 제거한다`() {
        val (token, userId) = issueTokenForNewUser()
        mockMvc.perform(post("/api/v1/notifications/device-token").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content("""{"token":"ExponentPushToken[api-2]","platform":"IOS"}"""))
            .andExpect(status().isNoContent)

        mockMvc.perform(delete("/api/v1/notifications/device-token").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content("""{"token":"ExponentPushToken[api-2]"}"""))
            .andExpect(status().isNoContent)

        assertTrue(deviceTokenRepository.findAllByUserId(userId).isEmpty())
    }

    @Test
    fun `GET settings 호출시 설정이 없으면 기본값을 반환한다`() {
        val (token, _) = issueTokenForNewUser()

        mockMvc.perform(get("/api/v1/notifications/settings").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.routineReminderEnabled").value(true))
            .andExpect(jsonPath("$.inactivityAlertEnabled").value(true))
            .andExpect(jsonPath("$.summaryNotificationEnabled").value(true))
    }

    @Test
    fun `PATCH settings 호출하면 지정한 필드만 변경된다`() {
        val (token, _) = issueTokenForNewUser()

        mockMvc.perform(patch("/api/v1/notifications/settings").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content("""{"routineReminderEnabled":false}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.routineReminderEnabled").value(false))
            .andExpect(jsonPath("$.inactivityAlertEnabled").value(true))
    }
}
