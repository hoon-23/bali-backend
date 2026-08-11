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

class GoogleTokenVerifierTest {

    private val rsaKey: RSAKey = RSAKeyGenerator(2048).keyID("test-kid").generate()

    private val googleTokenVerifier = GoogleTokenVerifier(
        OidcIdTokenVerifier(
            jwkSetSupplier = JwkSetSupplier { JWKSet(rsaKey.toPublicJWK()) },
            expectedIssuer = "https://accounts.google.com",
            expectedAudience = "test-google-client-id",
        )
    )

    private fun signedToken(subject: String? = null, email: String?): String {
        val builder = JWTClaimsSet.Builder()
            .issuer("https://accounts.google.com")
            .audience("test-google-client-id")
            .expirationTime(Date(System.currentTimeMillis() + 3600_000))
        if (subject != null) builder.subject(subject)
        if (email != null) builder.claim("email", email)
        val header = JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.keyID).build()
        val signedJwt = SignedJWT(header, builder.build())
        signedJwt.sign(RSASSASigner(rsaKey))
        return signedJwt.serialize()
    }

    @Test
    fun `provider는 GOOGLE이다`() {
        assertEquals(AuthProvider.GOOGLE, googleTokenVerifier.provider)
    }

    @Test
    fun `sub와 email을 SocialUserInfo로 변환한다`() {
        val info = googleTokenVerifier.verify(signedToken(subject = "google-sub-1", email = "a@example.com"))
        assertEquals("google-sub-1", info.providerId)
        assertEquals("a@example.com", info.email)
    }

    @Test
    fun `sub와 email이 없으면 email이 null이다`() {
        val info = googleTokenVerifier.verify(signedToken(subject = "google-sub-2", email = null))
        assertEquals("google-sub-2", info.providerId)
        assertNull(info.email)
    }

    @Test
    fun `sub가 없으면 IllegalArgumentException을 던진다`() {
        val token = signedToken(subject = null, email = "a@example.com")
        assertThrows(IllegalArgumentException::class.java) {
            googleTokenVerifier.verify(token)
        }
    }
}
