package com.bali.api.auth.oauth2

import com.bali.core.user.AuthProvider
import com.bali.core.user.User
import com.bali.core.user.UserRepository
import com.bali.core.user.UserStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

// CustomOAuth2UserService의 사용자 조회/생성 로직을 검증하는 테스트
class CustomOAuth2UserServiceTest {

    // 최초 로그인 시 새로운 사용자가 생성되는지 검증
    @Test
    fun `resolveUser creates a new user on first login`() {
        val repository = InMemoryUserRepository()
        val service = CustomOAuth2UserService(repository)

        val user = service.resolveUser(providerId = "google-sub-1", email = "new@example.com")

        assertEquals("new@example.com", user.email)
        assertEquals(AuthProvider.GOOGLE, user.provider)
        assertEquals(UserStatus.ACTIVE, user.status)
    }

    // 동일한 프로바이더 ID로 재로그인 시 기존 사용자가 반환되는지 검증
    @Test
    fun `resolveUser returns the existing user on repeat login`() {
        val repository = InMemoryUserRepository()
        val service = CustomOAuth2UserService(repository)

        val first = service.resolveUser(providerId = "google-sub-2", email = "again@example.com")
        val second = service.resolveUser(providerId = "google-sub-2", email = "again@example.com")

        assertEquals(first.id, second.id)
    }

    // 테스트 전용 인메모리 UserRepository 구현체
    private class InMemoryUserRepository : UserRepository {
        private val store = mutableMapOf<UUID, User>()

        // ID로 사용자를 조회
        override fun findById(id: UUID): User? = store[id]

        // 프로바이더와 프로바이더 ID로 사용자를 조회
        override fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): User? =
            store.values.find { it.provider == provider && it.providerId == providerId }

        // 사용자를 저장 (ID가 없으면 새로 발급)
        override fun save(user: User): User {
            val toSave = user.copy(id = user.id ?: UUID.randomUUID())
            store[toSave.id!!] = toSave
            return toSave
        }

        // 특정 상태의 사용자 전체 목록을 조회
        override fun findAllByStatus(status: UserStatus): List<User> =
            store.values.filter { it.status == status }
    }
}
