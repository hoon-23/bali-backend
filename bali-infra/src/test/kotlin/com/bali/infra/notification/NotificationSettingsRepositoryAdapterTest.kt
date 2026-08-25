package com.bali.infra.notification

import com.bali.core.notification.NotificationSettings
import com.bali.infra.InfraTestConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = [InfraTestConfig::class])
@Import(NotificationSettingsRepositoryAdapter::class)
class NotificationSettingsRepositoryAdapterTest {

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

    @Autowired lateinit var adapter: NotificationSettingsRepositoryAdapter

    @Test
    fun `행이 없는 유저는 null을 반환한다`() {
        assertNull(adapter.findByUserId(UUID.randomUUID()))
    }

    @Test
    fun `저장한 설정을 그대로 조회할 수 있다`() {
        val userId = UUID.randomUUID()
        adapter.save(NotificationSettings(userId = userId, routineReminderEnabled = false, inactivityAlertEnabled = true, summaryNotificationEnabled = false))

        val found = adapter.findByUserId(userId)

        assertEquals(false, found?.routineReminderEnabled)
        assertEquals(true, found?.inactivityAlertEnabled)
        assertEquals(false, found?.summaryNotificationEnabled)
    }
}
