package com.bali.api.auth.social

import com.bali.core.auth.AppleRefreshTokenRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class AppleAccountServiceTest {

    private class FakeClient(var exchangeResult: String? = "apple-refresh", var revokeResult: Boolean = true) : AppleAuthClient {
        val exchanged = mutableListOf<String>()
        val revoked = mutableListOf<String>()
        override fun exchangeCode(authorizationCode: String): String? { exchanged += authorizationCode; return exchangeResult }
        override fun revoke(refreshToken: String): Boolean { revoked += refreshToken; return revokeResult }
    }

    private class FakeRepo : AppleRefreshTokenRepository {
        val store = mutableMapOf<UUID, String>()
        override fun save(userId: UUID, refreshToken: String) { store[userId] = refreshToken }
        override fun findByUserId(userId: UUID): String? = store[userId]
        override fun deleteByUserId(userId: UUID) { store.remove(userId) }
    }

    private val userId = UUID.randomUUID()

    @Test
    fun `코드가 있으면 교환해서 저장한다`() {
        val client = FakeClient(); val repo = FakeRepo()
        AppleAccountService(client, repo).storeRefreshToken(userId, "code-1")

        assertEquals(listOf("code-1"), client.exchanged)
        assertEquals("apple-refresh", repo.findByUserId(userId))
    }

    // 구버전 앱(1.5.1 이하)은 authorizationCode 없이 로그인하므로 아무 것도 하지 않아야 한다
    @Test
    fun `코드가 없거나 비어 있으면 아무 것도 하지 않는다`() {
        val client = FakeClient(); val repo = FakeRepo()
        val service = AppleAccountService(client, repo)

        service.storeRefreshToken(userId, null)
        service.storeRefreshToken(userId, " ")

        assertEquals(emptyList<String>(), client.exchanged)
        assertNull(repo.findByUserId(userId))
    }

    @Test
    fun `교환이 실패하면 저장하지 않는다`() {
        val repo = FakeRepo()
        AppleAccountService(FakeClient(exchangeResult = null), repo).storeRefreshToken(userId, "code-1")

        assertNull(repo.findByUserId(userId))
    }

    @Test
    fun `탈퇴 시 저장된 토큰을 revoke하고 삭제한다`() {
        val client = FakeClient(); val repo = FakeRepo().also { it.save(userId, "apple-refresh") }
        AppleAccountService(client, repo).revokeOnWithdraw(userId)

        assertEquals(listOf("apple-refresh"), client.revoked)
        assertNull(repo.findByUserId(userId))
    }

    // Apple 호출이 실패해도 탈퇴는 계속되어야 하므로 예외 없이 토큰만 삭제한다
    @Test
    fun `revoke가 실패해도 예외 없이 토큰을 삭제한다`() {
        val repo = FakeRepo().also { it.save(userId, "apple-refresh") }
        AppleAccountService(FakeClient(revokeResult = false), repo).revokeOnWithdraw(userId)

        assertNull(repo.findByUserId(userId))
    }

    @Test
    fun `저장된 토큰이 없으면 revoke를 호출하지 않는다`() {
        val client = FakeClient()
        AppleAccountService(client, FakeRepo()).revokeOnWithdraw(userId)

        assertEquals(emptyList<String>(), client.revoked)
    }
}
