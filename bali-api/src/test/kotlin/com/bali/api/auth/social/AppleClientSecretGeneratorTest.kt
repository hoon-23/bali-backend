package com.bali.api.auth.social

import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.Base64

class AppleClientSecretGeneratorTest {

    private val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    // 실제 줄바꿈이 있는 PEM을 만들고, 필요하면 줄바꿈을 리터럴 \n 두 글자로 바꾼다
    private fun pem(newline: String): String {
        val body = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(keyPair.private.encoded)
        return "-----BEGIN PRIVATE KEY-----\n$body\n-----END PRIVATE KEY-----".replace("\n", newline)
    }

    // Apple이 요구하는 클레임/헤더로 ES256 서명된 JWT가 만들어지는지 확인
    @Test
    fun `client secret은 Apple 규격의 ES256 JWT다`() {
        val now = Instant.parse("2026-10-20T00:00:00Z")
        val generator = AppleClientSecretGenerator("TEAM123456", "8CQ39HXF35", pem("\n"), "com.younghoon.swayt")

        val jwt = SignedJWT.parse(generator.generate(now))

        assertTrue(jwt.verify(ECDSAVerifier(keyPair.public as ECPublicKey)))
        assertEquals("ES256", jwt.header.algorithm.name)
        assertEquals("8CQ39HXF35", jwt.header.keyID)
        assertEquals("TEAM123456", jwt.jwtClaimsSet.issuer)
        assertEquals("com.younghoon.swayt", jwt.jwtClaimsSet.subject)
        assertEquals(listOf("https://appleid.apple.com"), jwt.jwtClaimsSet.audience)
        assertEquals(now.plusSeconds(300), jwt.jwtClaimsSet.expirationTime.toInstant())
    }

    // 환경변수로 키를 넘길 때 줄바꿈이 리터럴 \n 두 글자로 들어와도 읽혀야 한다
    @Test
    fun `줄바꿈이 리터럴 백슬래시n인 키도 읽는다`() {
        val generator = AppleClientSecretGenerator("T", "K", pem("\\n"), "c")

        val jwt = SignedJWT.parse(generator.generate())

        assertTrue(jwt.verify(ECDSAVerifier(keyPair.public as ECPublicKey)))
    }
}
