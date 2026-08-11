package com.bali.api.auth.social

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.Date

class OidcIdTokenVerifierTest {

    private val rsaKey: RSAKey = RSAKeyGenerator(2048).keyID("test-kid").generate()
    private val fakeJwkSetSupplier = JwkSetSupplier { JWKSet(rsaKey.toPublicJWK()) }

    private val verifier = OidcIdTokenVerifier(
        jwkSetSupplier = fakeJwkSetSupplier,
        expectedIssuer = "https://example-issuer.test",
        expectedAudience = "expected-client-id",
    )

    // 지정한 클레임으로 rsaKey의 개인키로 서명한 JWT를 만든다(테스트 전용 헬퍼)
    private fun signedToken(
        issuer: String = "https://example-issuer.test",
        audience: String = "expected-client-id",
        subject: String = "user-sub-1",
        expiresInMillis: Long = 3600_000,
        signingKey: RSAKey = rsaKey,
    ): String {
        val claims = JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(audience)
            .subject(subject)
            .expirationTime(Date(System.currentTimeMillis() + expiresInMillis))
            .build()
        val header = JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.keyID).build()
        val signedJwt = SignedJWT(header, claims)
        signedJwt.sign(RSASSASigner(signingKey))
        return signedJwt.serialize()
    }

    @Test
    fun `유효한 토큰은 클레임을 그대로 반환한다`() {
        val claims = verifier.verify(signedToken())
        assertEquals("user-sub-1", claims.subject)
    }

    @Test
    fun `발급자가 다르면 예외를 던진다`() {
        assertThrows(IllegalArgumentException::class.java) {
            verifier.verify(signedToken(issuer = "https://wrong-issuer.test"))
        }
    }

    @Test
    fun `대상이 다르면 예외를 던진다`() {
        assertThrows(IllegalArgumentException::class.java) {
            verifier.verify(signedToken(audience = "wrong-client-id"))
        }
    }

    @Test
    fun `만료된 토큰은 예외를 던진다`() {
        assertThrows(IllegalArgumentException::class.java) {
            verifier.verify(signedToken(expiresInMillis = -1000))
        }
    }

    @Test
    fun `다른 키로 서명된 토큰은 예외를 던진다`() {
        val otherKey = RSAKeyGenerator(2048).keyID("test-kid").generate()
        assertThrows(IllegalArgumentException::class.java) {
            verifier.verify(signedToken(signingKey = otherKey))
        }
    }
}
