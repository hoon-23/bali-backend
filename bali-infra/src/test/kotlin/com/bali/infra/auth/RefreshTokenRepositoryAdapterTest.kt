package com.bali.infra.auth

import com.bali.core.auth.RefreshToken
import com.bali.core.user.AuthProvider
import com.bali.core.user.User
import com.bali.core.user.UserStatus
import com.bali.infra.InfraTestConfig
import com.bali.infra.user.UserRepositoryAdapter
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
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = [InfraTestConfig::class])
@Import(RefreshTokenRepositoryAdapter::class, UserRepositoryAdapter::class)
class RefreshTokenRepositoryAdapterTest {

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

    @Autowired
    lateinit var adapter: RefreshTokenRepositoryAdapter

    @Autowired
    lateinit var userRepositoryAdapter: UserRepositoryAdapter

    // refresh_tokens.user_id는 users(id)를 참조하는 FK라 먼저 실제 유저를 만들어야 한다
    private fun newUserId(): UUID = userRepositoryAdapter.save(
        User(
            id = null, email = "refresh-token-test-${UUID.randomUUID()}@example.com",
            provider = AuthProvider.GOOGLE, providerId = "google-sub-${UUID.randomUUID()}",
            status = UserStatus.ACTIVE, createdAt = Instant.now(),
        )
    ).id!!

    private fun newToken(userId: UUID) = RefreshToken(
        id = null, userId = userId, tokenHash = "hash-${UUID.randomUUID()}",
        expiresAt = Instant.now().plusSeconds(3600),
    )

    @Test
    fun `활성 토큰을 revokeIfActive 하면 true를 반환하고 실제로 폐기된다`() {
        val saved = adapter.save(newToken(newUserId()))

        val result = adapter.revokeIfActive(saved.id!!)

        assertTrue(result)
        assertEquals(true, adapter.findByTokenHash(saved.tokenHash)?.revoked)
    }

    @Test
    fun `이미 폐기된 토큰을 다시 revokeIfActive 하면 false를 반환한다`() {
        val saved = adapter.save(newToken(newUserId()))
        adapter.revokeIfActive(saved.id!!)

        val result = adapter.revokeIfActive(saved.id!!)

        assertFalse(result)
    }

    @Test
    fun `존재하지 않는 id를 revokeIfActive 하면 false를 반환한다`() {
        assertFalse(adapter.revokeIfActive(UUID.randomUUID()))
    }

    // 프론트에서 401을 여러 화면이 동시에 만나 같은 refresh token으로 두 번 /auth/refresh를
    // 호출하는 race condition을 흉내낸다. 두 스레드가 각자 독립된 트랜잭션/커넥션으로 정확히
    // 같은 시점에 같은 토큰을 revokeIfActive 하더라도, 단 하나만 성공해야 두 배로 새 토큰 쌍이
    // 발급되는 걸 막을 수 있다. @Transactional(NOT_SUPPORTED)로 @DataJpaTest의 기본 테스트
    // 트랜잭션을 걷어내야 각 스레드가 실제로 커밋된 별도 트랜잭션에서 경합하게 된다.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `동시에 같은 토큰을 revokeIfActive 하면 정확히 하나만 성공한다`() {
        val saved = adapter.save(newToken(newUserId()))
        val executor = Executors.newFixedThreadPool(2)
        val barrier = CyclicBarrier(2)

        try {
            val futures = (1..2).map {
                executor.submit<Boolean> {
                    barrier.await(5, TimeUnit.SECONDS)
                    adapter.revokeIfActive(saved.id!!)
                }
            }
            val results = futures.map { it.get(5, TimeUnit.SECONDS) }

            assertEquals(1, results.count { it }, "정확히 한 요청만 rotation에 성공해야 한다")
            assertEquals(1, results.count { !it }, "나머지 한 요청은 이미 사용된 토큰으로 처리되어야 한다")
        } finally {
            executor.shutdownNow()
        }
    }
}
