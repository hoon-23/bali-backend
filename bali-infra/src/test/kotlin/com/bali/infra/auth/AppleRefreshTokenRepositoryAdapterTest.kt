package com.bali.infra.auth

import com.bali.core.user.AuthProvider
import com.bali.core.user.User
import com.bali.core.user.UserStatus
import com.bali.infra.InfraTestConfig
import com.bali.infra.user.UserRepositoryAdapter
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
import java.time.Instant
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = [InfraTestConfig::class])
@Import(AppleRefreshTokenRepositoryAdapter::class, UserRepositoryAdapter::class)
class AppleRefreshTokenRepositoryAdapterTest {

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

    @Autowired lateinit var adapter: AppleRefreshTokenRepositoryAdapter
    @Autowired lateinit var userRepositoryAdapter: UserRepositoryAdapter

    // apple_refresh_tokens.user_id는 users(id) FK라 실제 유저가 먼저 필요하다
    private fun newUserId(): UUID = userRepositoryAdapter.save(
        User(
            id = null, email = "apple-token-test-${UUID.randomUUID()}@example.com",
            provider = AuthProvider.APPLE, providerId = "apple-sub-${UUID.randomUUID()}",
            status = UserStatus.ACTIVE, createdAt = Instant.now(),
        )
    ).id!!

    @Test
    fun `저장하고 조회하고 삭제한다`() {
        val userId = newUserId()

        adapter.save(userId, "refresh-1")
        assertEquals("refresh-1", adapter.findByUserId(userId))

        adapter.deleteByUserId(userId)
        assertNull(adapter.findByUserId(userId))
    }

    // 재로그인으로 새 코드를 교환하면 같은 유저의 토큰이 새 값으로 교체되어야 한다
    @Test
    fun `같은 유저에 다시 저장하면 값이 교체된다`() {
        val userId = newUserId()

        adapter.save(userId, "refresh-1")
        adapter.save(userId, "refresh-2")

        assertEquals("refresh-2", adapter.findByUserId(userId))
    }

    @Test
    fun `없는 유저를 삭제해도 예외가 없다`() {
        adapter.deleteByUserId(UUID.randomUUID())
    }
}
