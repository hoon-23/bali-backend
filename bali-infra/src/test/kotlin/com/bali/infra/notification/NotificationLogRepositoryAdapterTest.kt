package com.bali.infra.notification

import com.bali.core.notification.DeliveryStatus
import com.bali.core.notification.NotificationLog
import com.bali.core.notification.NotificationType
import com.bali.infra.InfraTestConfig
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
}
