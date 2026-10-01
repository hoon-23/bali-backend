package com.bali.infra.notification

import com.bali.core.notification.DeliveryStatus
import com.bali.core.notification.NotificationLog
import com.bali.core.notification.NotificationType
import com.bali.infra.InfraTestConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = [InfraTestConfig::class])
@Import(NotificationLogRepositoryAdapter::class)
class NotificationLogRepositoryAdapterTest {

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { "jdbc:postgresql://localhost:5432/bali" }
            registry.add("spring.datasource.username") { "bali" }
            registry.add("spring.datasource.password") { "bali" }
            registry.add("spring.jpa.hibernate.ddl-auto") { "validate" }
        }
    }

    @Autowired lateinit var adapter: NotificationLogRepositoryAdapter

    @Test
    fun `동일 type reference_id로 저장하면 existsByTypeAndReferenceId가 true를 반환한다`() {
        val referenceId = UUID.randomUUID()
        adapter.save(NotificationLog(id = null, userId = UUID.randomUUID(), type = NotificationType.ROUTINE_REMINDER, referenceId = referenceId, expoTicketId = "ticket-1", deliveryStatus = DeliveryStatus.PENDING, deliveryError = null, sentAt = Instant.now()))

        assertTrue(adapter.existsByTypeAndReferenceId(NotificationType.ROUTINE_REMINDER, referenceId))
        assertFalse(adapter.existsByTypeAndReferenceId(NotificationType.INACTIVITY_ALERT, referenceId))
    }

    @Test
    fun `sentAt이 기준 시각 이후면 existsByUserIdAndTypeSentAfter가 true를 반환한다`() {
        val userId = UUID.randomUUID()
        adapter.save(NotificationLog(id = null, userId = userId, type = NotificationType.INACTIVITY_ALERT, referenceId = null, expoTicketId = "ticket-2", deliveryStatus = DeliveryStatus.PENDING, deliveryError = null, sentAt = Instant.now()))

        assertTrue(adapter.existsByUserIdAndTypeSentAfter(userId, NotificationType.INACTIVITY_ALERT, Instant.now().minus(1, ChronoUnit.DAYS)))
        assertFalse(adapter.existsByUserIdAndTypeSentAfter(userId, NotificationType.INACTIVITY_ALERT, Instant.now().plus(1, ChronoUnit.DAYS)))
    }

    private fun log(userId: UUID, sentAt: Instant, readAt: Instant? = null, title: String = "제목") =
        NotificationLog(id = null, userId = userId, type = NotificationType.WEEKLY_SUMMARY, referenceId = UUID.randomUUID(), expoTicketId = "t", deliveryStatus = DeliveryStatus.PENDING, deliveryError = null, sentAt = sentAt, title = title, body = "본문", readAt = readAt)

    // 30일 이내 알림만 sentAt 내림차순으로 조회하고 totalElements를 반환하는지 확인
    @Test
    fun `findPageByUserIdSentAfter는 기준 이후 알림만 최신순으로 반환한다`() {
        val userId = UUID.randomUUID()
        val now = Instant.now()
        adapter.save(log(userId, now.minus(40, ChronoUnit.DAYS), title = "오래됨"))
        adapter.save(log(userId, now.minus(2, ChronoUnit.DAYS), title = "이틀전"))
        adapter.save(log(userId, now.minus(1, ChronoUnit.DAYS), title = "어제"))
        adapter.save(log(UUID.randomUUID(), now.minus(1, ChronoUnit.DAYS), title = "다른유저"))

        val page = adapter.findPageByUserIdSentAfter(userId, now.minus(30, ChronoUnit.DAYS), page = 0, size = 10)

        assertEquals(listOf("어제", "이틀전"), page.items.map { it.title })
        assertEquals(2L, page.totalElements)
    }

    // 페이지 크기 단위로 잘려서 반환되는지 확인
    @Test
    fun `findPageByUserIdSentAfter는 size 단위로 페이지를 나눈다`() {
        val userId = UUID.randomUUID()
        val now = Instant.now()
        (1..3).forEach { adapter.save(log(userId, now.minus(it.toLong(), ChronoUnit.HOURS), title = "n$it")) }

        val second = adapter.findPageByUserIdSentAfter(userId, now.minus(30, ChronoUnit.DAYS), page = 1, size = 2)

        assertEquals(listOf("n3"), second.items.map { it.title })
        assertEquals(3L, second.totalElements)
    }

    // 읽은 알림과 31일 전 알림은 안 읽은 개수에서 제외되는지 확인
    @Test
    fun `countUnreadByUserIdSentAfter는 안 읽은 최근 알림만 센다`() {
        val userId = UUID.randomUUID()
        val now = Instant.now()
        adapter.save(log(userId, now.minus(1, ChronoUnit.DAYS)))
        adapter.save(log(userId, now.minus(2, ChronoUnit.DAYS), readAt = now))
        adapter.save(log(userId, now.minus(31, ChronoUnit.DAYS)))

        assertEquals(1L, adapter.countUnreadByUserIdSentAfter(userId, now.minus(30, ChronoUnit.DAYS)))
    }

    // 본인 알림만 읽음 처리되고, 이미 읽은 알림은 최초 read_at을 유지하는지 확인
    @Test
    fun `markRead는 소유권을 확인하고 최초 읽은 시각을 유지한다`() {
        val owner = UUID.randomUUID()
        val saved = adapter.save(log(owner, Instant.now()))
        val first = Instant.now().minus(1, ChronoUnit.HOURS)

        assertEquals(false, adapter.markRead(UUID.randomUUID(), saved.id!!, first))
        assertEquals(true, adapter.markRead(owner, saved.id!!, first))
        assertEquals(true, adapter.markRead(owner, saved.id!!, Instant.now()))

        val readAt = adapter.findPageByUserIdSentAfter(owner, Instant.now().minus(30, ChronoUnit.DAYS), 0, 10).items.single().readAt
        assertEquals(first.toEpochMilli(), readAt!!.toEpochMilli())
    }

    // 존재하지 않는 id는 false를 반환하는지 확인
    @Test
    fun `markRead는 없는 id에 false를 반환한다`() {
        assertEquals(false, adapter.markRead(UUID.randomUUID(), UUID.randomUUID(), Instant.now()))
    }

    // 전체 읽음은 본인 안 읽은 행만 갱신하고 다른 유저 행은 건드리지 않는지 확인
    @Test
    fun `markAllRead는 본인 안 읽은 알림만 읽음 처리한다`() {
        val me = UUID.randomUUID()
        val other = UUID.randomUUID()
        val now = Instant.now()
        adapter.save(log(me, now.minus(1, ChronoUnit.HOURS)))
        adapter.save(log(me, now.minus(2, ChronoUnit.HOURS)))
        adapter.save(log(other, now.minus(1, ChronoUnit.HOURS)))

        val updated = adapter.markAllRead(me, now)

        assertEquals(2, updated)
        assertEquals(0L, adapter.countUnreadByUserIdSentAfter(me, now.minus(30, ChronoUnit.DAYS)))
        assertEquals(1L, adapter.countUnreadByUserIdSentAfter(other, now.minus(30, ChronoUnit.DAYS)))
    }
}
