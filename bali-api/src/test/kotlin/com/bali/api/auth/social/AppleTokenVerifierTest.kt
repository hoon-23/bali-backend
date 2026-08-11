package com.bali.api.auth.social

import com.bali.core.user.AuthProvider
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.Date

class AppleTokenVerifierTest {

    private val rsaKey: RSAKey = RSAKeyGenerator(2048).keyID("test-kid").generate()

    private val appleTokenVerifier = AppleTokenVerifier(
        OidcIdTokenVerifier(
            jwkSetSupplier = JwkSetSupplier { JWKSet(rsaKey.toPublicJWK()) },
            expectedIssuer = "https://appleid.apple.com",
            expectedAudience = "com.bali.app",
        )
    )

    private fun signedToken(subject: String? = null, email: String? = null): String {
        val builder = JWTClaimsSet.Builder()
            .issuer("https://appleid.apple.com")
            .audience("com.bali.app")
            .expirationTime(Date(System.currentTimeMillis() + 3600_000))
        if (subject != null) builder.subject(subject)
        if (email != null) builder.claim("email", email)
        val header = JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.keyID).build()
        val signedJwt = SignedJWT(header, builder.build())
        signedJwt.sign(RSASSASigner(rsaKey))
        return signedJwt.serialize()
    }

    @Test
    fun `provider는 APPLE이다`() {
        assertEquals(AuthProvider.APPLE, appleTokenVerifier.provider)
    }

    @Test
    fun `sub는 providerId로 변환되고 email은 항상 null이다`() {
        val info = appleTokenVerifier.verify(signedToken(subject = "apple-sub-1"))
        assertEquals("apple-sub-1", info.providerId)
        assertNull(info.email)
    }

    @Test
    fun `email이 토큰에 있어도 항상 null을 반환한다`() {
        val info = appleTokenVerifier.verify(signedToken(subject = "apple-sub-2", email = "test@example.com"))
        assertEquals("apple-sub-2", info.providerId)
        assertNull(info.email)
    }

    @Test
    fun `sub가 없으면 IllegalArgumentException을 던진다`() {
        val token = signedToken(subject = null, email = "test@example.com")
        assertThrows(IllegalArgumentException::class.java) {
            appleTokenVerifier.verify(token)
        }
    }
}
