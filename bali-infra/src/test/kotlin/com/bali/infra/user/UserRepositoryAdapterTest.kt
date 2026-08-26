package com.bali.infra.user

import com.bali.core.user.AuthProvider
import com.bali.core.user.User
import com.bali.core.user.UserStatus
import com.bali.infra.InfraTestConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Instant

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = [InfraTestConfig::class])
@Import(UserRepositoryAdapter::class)
class UserRepositoryAdapterTest {

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun props(registry: DynamicPropertyRegistry) {
            // Mac Docker Desktop에서 docker-java 호환성 문제로 Testcontainers를 쓸 수 없어, docker-compose의 로컬 PostgreSQL(localhost:5432)을 사용.
            registry.add("spring.datasource.url") { "jdbc:postgresql://localhost:5432/bali" }
            registry.add("spring.datasource.username") { "bali" }
            registry.add("spring.datasource.password") { "bali" }
            registry.add("spring.jpa.hibernate.ddl-auto") { "validate" }
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    lateinit var adapter: UserRepositoryAdapter

    @Test
    fun `save then findByProviderAndProviderId returns the same user`() {
        val saved = adapter.save(
            User(
                id = null,
                email = "test@example.com",
                provider = AuthProvider.GOOGLE,
                providerId = "google-sub-123",
                status = UserStatus.ACTIVE,
                createdAt = Instant.now(),
            )
        )

        val found = adapter.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-123")

        assertEquals(saved.id, found?.id)
        assertEquals("test@example.com", found?.email)
    }

    @Test
    fun `findByProviderAndProviderId returns null when no match`() {
        val found = adapter.findByProviderAndProviderId(AuthProvider.GOOGLE, "does-not-exist")
        assertNull(found)
    }

    @Test
    fun `findAllByStatus는 해당 상태의 유저만 반환한다`() {
        val active = adapter.save(
            User(id = null, email = "active-${System.nanoTime()}@example.com", provider = AuthProvider.GOOGLE, providerId = "sub-active-${System.nanoTime()}", status = UserStatus.ACTIVE, createdAt = Instant.now())
        )
        adapter.save(
            User(id = null, email = "withdrawn-${System.nanoTime()}@example.com", provider = AuthProvider.GOOGLE, providerId = "sub-withdrawn-${System.nanoTime()}", status = UserStatus.WITHDRAWN, createdAt = Instant.now())
        )

        val activeUsers = adapter.findAllByStatus(UserStatus.ACTIVE)

        assertEquals(true, activeUsers.any { it.id == active.id })
        assertEquals(true, activeUsers.none { it.status == UserStatus.WITHDRAWN })
    }

    @Test
    fun `findByEmail은 이메일이 일치하는 유저를 반환하고, 없으면 null을 반환한다`() {
        val email = "find-by-email-${System.nanoTime()}@example.com"
        val saved = adapter.save(
            User(id = null, email = email, provider = AuthProvider.GOOGLE, providerId = "sub-find-by-email-${System.nanoTime()}", status = UserStatus.ACTIVE, createdAt = Instant.now())
        )

        assertEquals(saved.id, adapter.findByEmail(email)?.id)
        assertNull(adapter.findByEmail("does-not-exist-${System.nanoTime()}@example.com"))
    }

    @Test
    fun `NAVER, KAKAO, APPLE provider로도 저장과 조회가 가능하다`() {
        listOf(AuthProvider.NAVER, AuthProvider.KAKAO, AuthProvider.APPLE).forEach { provider ->
            val saved = adapter.save(
                User(
                    id = null,
                    email = "test-${provider.name.lowercase()}@example.com",
                    provider = provider,
                    providerId = "sub-${provider.name.lowercase()}",
                    status = UserStatus.ACTIVE,
                    createdAt = Instant.now(),
                )
            )

            val found = adapter.findByProviderAndProviderId(provider, "sub-${provider.name.lowercase()}")

            assertEquals(saved.id, found?.id)
        }
    }

    @Test
    fun `save then findByProviderAndProviderId returns nickname and weeklyGoalSessions`() {
        val saved = adapter.save(
            User(
                id = null,
                email = "profile-${System.nanoTime()}@example.com",
                provider = AuthProvider.GOOGLE,
                providerId = "sub-profile-${System.nanoTime()}",
                status = UserStatus.ACTIVE,
                createdAt = Instant.now(),
                nickname = "행복한옥수수07",
                weeklyGoalSessions = 5,
            )
        )

        val found = adapter.findByProviderAndProviderId(AuthProvider.GOOGLE, saved.providerId)

        assertEquals("행복한옥수수07", found?.nickname)
        assertEquals(5, found?.weeklyGoalSessions)
    }
}
