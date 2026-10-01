package com.bali.api.notification

import com.bali.api.auth.jwt.JwtTokenProvider
import com.bali.core.notification.DeliveryStatus
import com.bali.core.notification.NotificationLog
import com.bali.core.notification.NotificationLogRepository
import com.bali.core.notification.NotificationType
import com.bali.core.user.AuthProvider
import com.bali.infra.user.UserJpaEntity
import com.bali.infra.user.UserJpaRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationInboxControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired lateinit var userJpaRepository: UserJpaRepository
    @Autowired lateinit var logRepository: NotificationLogRepository

    // 테스트용 사용자를 만들고 (JWT, userId)를 반환
    private fun newUser(): Pair<String, UUID> {
        val entity = userJpaRepository.save(
            UserJpaEntity(email = "inbox-test-${System.nanoTime()}@example.com", provider = AuthProvider.GOOGLE, providerId = "sub-inbox-${System.nanoTime()}")
        )
        return jwtTokenProvider.generateToken(entity.id, entity.email) to entity.id
    }

    // 알림 로그 1건을 저장하고 id 반환
    private fun saveLog(userId: UUID, sentAt: Instant, title: String = "제목", readAt: Instant? = null, referenceId: UUID? = UUID.randomUUID()): UUID =
        logRepository.save(
            NotificationLog(id = null, userId = userId, type = NotificationType.WEEKLY_SUMMARY, referenceId = referenceId, expoTicketId = "t", deliveryStatus = DeliveryStatus.PENDING, deliveryError = null, sentAt = sentAt, title = title, body = "본문", readAt = readAt)
        ).id!!

    @Test
    fun `토큰 없이 알림함 목록을 호출하면 401`() {
        mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `목록은 최근 30일 알림을 최신순으로 반환하고 응답 형태를 지킨다`() {
        val (token, userId) = newUser()
        val now = Instant.now()
        saveLog(userId, now.minus(31, ChronoUnit.DAYS), title = "오래됨")
        saveLog(userId, now.minus(2, ChronoUnit.DAYS), title = "이틀전")
        saveLog(userId, now.minus(1, ChronoUnit.DAYS), title = "어제", readAt = now)

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].title").value("어제"))
            .andExpect(jsonPath("$.items[0].read").value(true))
            .andExpect(jsonPath("$.items[0].type").value("WEEKLY_SUMMARY"))
            .andExpect(jsonPath("$.items[1].title").value("이틀전"))
            .andExpect(jsonPath("$.items[1].read").value(false))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.hasNext").value(false))
    }

    @Test
    fun `size와 page가 범위를 벗어나면 보정된다`() {
        val (token, userId) = newUser()
        (1..3).forEach { saveLog(userId, Instant.now().minus(it.toLong(), ChronoUnit.HOURS), title = "n$it") }

        mockMvc.perform(get("/api/v1/notifications").param("size", "2").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.hasNext").value(true))
        mockMvc.perform(get("/api/v1/notifications").param("size", "0").param("page", "-1").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.size").value(1))
            .andExpect(jsonPath("$.page").value(0))
        mockMvc.perform(get("/api/v1/notifications").param("size", "999").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.size").value(50))
    }

    @Test
    fun `referenceId가 없는 알림은 null로 내려간다`() {
        val (token, userId) = newUser()
        saveLog(userId, Instant.now(), referenceId = null)

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.items[0].referenceId").doesNotExist())
    }

    @Test
    fun `unread-count는 안 읽은 최근 알림 개수를 반환한다`() {
        val (token, userId) = newUser()
        val now = Instant.now()
        saveLog(userId, now.minus(1, ChronoUnit.DAYS))
        saveLog(userId, now.minus(2, ChronoUnit.DAYS), readAt = now)
        saveLog(userId, now.minus(31, ChronoUnit.DAYS))

        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.count").value(1))
    }

    @Test
    fun `PATCH read는 본인 알림을 읽음 처리하고 204를 반환한다`() {
        val (token, userId) = newUser()
        val id = saveLog(userId, Instant.now())

        mockMvc.perform(patch("/api/v1/notifications/$id/read").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)
        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.count").value(0))
    }

    @Test
    fun `다른 유저의 알림 id로 PATCH read하면 404이고 읽음 처리되지 않는다`() {
        val (token, _) = newUser()
        val (_, otherId) = newUser()
        val otherLog = saveLog(otherId, Instant.now())

        mockMvc.perform(patch("/api/v1/notifications/$otherLog/read").header("Authorization", "Bearer $token"))
            .andExpect(status().isNotFound)
        assertEquals(1L, logRepository.countUnreadByUserIdSentAfter(otherId, Instant.now().minus(30, ChronoUnit.DAYS)))
    }

    @Test
    fun `read-all은 본인 안 읽은 알림을 전부 읽음 처리한다`() {
        val (token, userId) = newUser()
        saveLog(userId, Instant.now().minus(1, ChronoUnit.HOURS))
        saveLog(userId, Instant.now().minus(2, ChronoUnit.HOURS))

        mockMvc.perform(post("/api/v1/notifications/read-all").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)
        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.count").value(0))
    }
}
