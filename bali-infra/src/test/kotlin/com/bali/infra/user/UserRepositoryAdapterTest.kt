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
            // Use docker-compose PostgreSQL on localhost:5432 due to Mac Docker Desktop docker-java compatibility issue.
            registry.add("spring.datasource.url") { "jdbc:postgresql://localhost:5432/bali" }
            registry.add("spring.datasource.username") { "bali" }
            registry.add("spring.datasource.password") { "bali" }
            registry.add("spring.jpa.hibernate.ddl-auto") { "create-drop" }
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
}
