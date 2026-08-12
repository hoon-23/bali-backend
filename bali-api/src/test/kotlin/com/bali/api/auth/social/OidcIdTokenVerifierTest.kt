package com.bali.api.auth.social

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
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

    // RSA 공개키의 modulus를 HMAC 비밀키로 재사용해 서명한 HS256 토큰(algorithm confusion 공격 시뮬레이션).
    // RSASSAVerifier는 HS256을 지원하지 않으므로 JOSEException이 나는데, 이게 IllegalArgumentException으로
    // 변환되는지가 회귀 테스트의 핵심이다
    @Test
    fun `RSA 공개키를 HMAC 비밀키로 사용한 알고리즘 혼동 공격은 예외를 던진다`() {
        val claims = JWTClaimsSet.Builder()
            .issuer("https://example-issuer.test")
            .audience("expected-client-id")
            .subject("attacker")
            .expirationTime(Date(System.currentTimeMillis() + 3600_000))
            .build()
        val header = JWSHeader.Builder(JWSAlgorithm.HS256).keyID(rsaKey.keyID).build()
        val forgedJwt = SignedJWT(header, claims)
        forgedJwt.sign(MACSigner(rsaKey.modulus.decode()))

        assertThrows(IllegalArgumentException::class.java) {
            verifier.verify(forgedJwt.serialize())
        }
    }

    @Test
    fun `kid가 없는 토큰은 예외를 던진다`() {
        val claims = JWTClaimsSet.Builder()
            .issuer("https://example-issuer.test")
            .audience("expected-client-id")
            .subject("user-sub-1")
            .expirationTime(Date(System.currentTimeMillis() + 3600_000))
            .build()
        val header = JWSHeader.Builder(JWSAlgorithm.RS256).build()
        val signedJwt = SignedJWT(header, claims)
        signedJwt.sign(RSASSASigner(rsaKey))

        assertThrows(IllegalArgumentException::class.java) {
            verifier.verify(signedJwt.serialize())
        }
    }

    @Test
    fun `JWKS에 일치하는 키가 전혀 없으면 예외를 던진다`() {
        val emptyJwksVerifier = OidcIdTokenVerifier(
            jwkSetSupplier = JwkSetSupplier { JWKSet() },
            expectedIssuer = "https://example-issuer.test",
            expectedAudience = "expected-client-id",
        )

        assertThrows(IllegalArgumentException::class.java) {
            emptyJwksVerifier.verify(signedToken())
        }
    }

    @Test
    fun `형식이 잘못된 토큰 문자열은 예외를 던진다`() {
        assertThrows(IllegalArgumentException::class.java) {
            verifier.verify("this-is-not-a-jwt")
        }
    }

    @Test
    fun `만료 시각(exp) 클레임이 없으면 예외를 던진다`() {
        val claims = JWTClaimsSet.Builder()
            .issuer("https://example-issuer.test")
            .audience("expected-client-id")
            .subject("user-sub-1")
            .build()
        val header = JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.keyID).build()
        val signedJwt = SignedJWT(header, claims)
        signedJwt.sign(RSASSASigner(rsaKey))

        assertThrows(IllegalArgumentException::class.java) {
            verifier.verify(signedJwt.serialize())
        }
    }

    @Test
    fun `대상(aud) 클레임이 없으면 예외를 던진다`() {
        val claims = JWTClaimsSet.Builder()
            .issuer("https://example-issuer.test")
            .subject("user-sub-1")
            .expirationTime(Date(System.currentTimeMillis() + 3600_000))
            .build()
        val header = JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.keyID).build()
        val signedJwt = SignedJWT(header, claims)
        signedJwt.sign(RSASSASigner(rsaKey))

        assertThrows(IllegalArgumentException::class.java) {
            verifier.verify(signedJwt.serialize())
        }
    }

    // 키 로테이션 시나리오: 초기 캐시(get())에는 새 kid가 없지만 강제 새로고침(getFresh())에는 있는 경우,
    // kid miss가 자동으로 강제 새로고침을 유발해 로테이션된 키로도 검증에 성공해야 한다
    @Test
    fun `캐시에 없는 kid는 강제 새로고침으로 로테이션된 키를 찾아 검증한다`() {
        val rotatedKey = RSAKeyGenerator(2048).keyID("rotated-kid").generate()
        var freshCallCount = 0
        val rotatingSupplier = object : JwkSetSupplier {
            override fun get(): JWKSet = JWKSet(rsaKey.toPublicJWK())
            override fun getFresh(): JWKSet {
                freshCallCount++
                return JWKSet(listOf(rsaKey.toPublicJWK(), rotatedKey.toPublicJWK()))
            }
        }
        val rotatingVerifier = OidcIdTokenVerifier(
            jwkSetSupplier = rotatingSupplier,
            expectedIssuer = "https://example-issuer.test",
            expectedAudience = "expected-client-id",
        )

        val claims = rotatingVerifier.verify(signedToken(signingKey = rotatedKey))

        assertEquals("user-sub-1", claims.subject)
        assertEquals(1, freshCallCount)
    }

    // expectedIssuers/expectedAudiences가 여러 값을 받을 수 있어야 한다(Google iss가 두 형태를 모두
    // 허용하거나, 모바일 클라이언트가 웹과 다른 aud를 쓰는 경우를 지원하기 위함 - 최종 리뷰 지적)
    @Test
    fun `여러 발급자 중 하나만 일치해도 통과한다`() {
        val multiIssuerVerifier = OidcIdTokenVerifier(
            jwkSetSupplier = fakeJwkSetSupplier,
            expectedIssuers = setOf("https://example-issuer.test", "https://alt-issuer.test"),
            expectedAudiences = setOf("expected-client-id"),
        )

        val claims = multiIssuerVerifier.verify(signedToken(issuer = "https://alt-issuer.test"))

        assertEquals("user-sub-1", claims.subject)
    }

    @Test
    fun `여러 대상 중 하나만 일치해도 통과한다`() {
        val multiAudienceVerifier = OidcIdTokenVerifier(
            jwkSetSupplier = fakeJwkSetSupplier,
            expectedIssuers = setOf("https://example-issuer.test"),
            expectedAudiences = setOf("web-client-id", "mobile-client-id"),
        )

        val claims = multiAudienceVerifier.verify(signedToken(audience = "mobile-client-id"))

        assertEquals("user-sub-1", claims.subject)
    }

    @Test
    fun `어떤 발급자와도 일치하지 않으면 예외를 던진다`() {
        val multiIssuerVerifier = OidcIdTokenVerifier(
            jwkSetSupplier = fakeJwkSetSupplier,
            expectedIssuers = setOf("https://example-issuer.test", "https://alt-issuer.test"),
            expectedAudiences = setOf("expected-client-id"),
        )

        assertThrows(IllegalArgumentException::class.java) {
            multiIssuerVerifier.verify(signedToken(issuer = "https://totally-wrong-issuer.test"))
        }
    }

    // kid가 이미 캐시에 있으면 강제 새로고침을 호출하지 않아야 한다(불필요한 네트워크 호출/fetch storm 방지)
    @Test
    fun `kid가 캐시에 있으면 강제 새로고침을 호출하지 않는다`() {
        val noRefreshSupplier = object : JwkSetSupplier {
            override fun get(): JWKSet = JWKSet(rsaKey.toPublicJWK())
            override fun getFresh(): JWKSet = throw AssertionError("캐시에 kid가 있는데 강제 새로고침이 호출됐다")
        }
        val noRefreshVerifier = OidcIdTokenVerifier(
            jwkSetSupplier = noRefreshSupplier,
            expectedIssuer = "https://example-issuer.test",
            expectedAudience = "expected-client-id",
        )

        val claims = noRefreshVerifier.verify(signedToken())

        assertEquals("user-sub-1", claims.subject)
    }
}
