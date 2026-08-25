package com.bali.infra.notification

import com.bali.core.notification.DevicePlatform
import com.bali.infra.InfraTestConfig
import org.junit.jupiter.api.Assertions.assertEquals
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
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = [InfraTestConfig::class])
@Import(DeviceTokenRepositoryAdapter::class)
class DeviceTokenRepositoryAdapterTest {

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

    @Autowired lateinit var adapter: DeviceTokenRepositoryAdapter

    @Test
    fun `동일 토큰으로 upsert하면 userId가 갱신된다`() {
        val firstUserId = UUID.randomUUID()
        val secondUserId = UUID.randomUUID()
        val token = "ExponentPushToken[test-${System.nanoTime()}]"

        adapter.upsert(com.bali.core.notification.DeviceToken(id = null, userId = firstUserId, expoPushToken = token, platform = DevicePlatform.IOS, createdAt = Instant.now(), updatedAt = Instant.now()))
        adapter.upsert(com.bali.core.notification.DeviceToken(id = null, userId = secondUserId, expoPushToken = token, platform = DevicePlatform.ANDROID, createdAt = Instant.now(), updatedAt = Instant.now()))

        assertTrue(adapter.findAllByUserId(firstUserId).isEmpty())
        assertEquals(1, adapter.findAllByUserId(secondUserId).size)
        assertEquals(DevicePlatform.ANDROID, adapter.findAllByUserId(secondUserId)[0].platform)
    }

    @Test
    fun `deleteByToken은 유저 무관하게 토큰을 삭제한다`() {
        val userId = UUID.randomUUID()
        val token = "ExponentPushToken[test-${System.nanoTime()}]"
        adapter.upsert(com.bali.core.notification.DeviceToken(id = null, userId = userId, expoPushToken = token, platform = DevicePlatform.IOS, createdAt = Instant.now(), updatedAt = Instant.now()))

        adapter.deleteByToken(token)

        assertTrue(adapter.findAllByUserId(userId).isEmpty())
    }

    @Test
    fun `deleteByUserIdAndToken은 userId가 일치할 때만 삭제한다`() {
        val ownerId = UUID.randomUUID()
        val otherId = UUID.randomUUID()
        val token = "ExponentPushToken[test-${System.nanoTime()}]"
        adapter.upsert(com.bali.core.notification.DeviceToken(id = null, userId = ownerId, expoPushToken = token, platform = DevicePlatform.IOS, createdAt = Instant.now(), updatedAt = Instant.now()))

        adapter.deleteByUserIdAndToken(otherId, token)
        assertEquals(1, adapter.findAllByUserId(ownerId).size)

        adapter.deleteByUserIdAndToken(ownerId, token)
        assertTrue(adapter.findAllByUserId(ownerId).isEmpty())
    }
}
