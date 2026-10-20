package com.bali.api.auth.social

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.security.KeyFactory
import java.security.interfaces.ECPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.Date

// Apple 토큰 엔드포인트(/auth/token, /auth/revoke) 호출용 client secret(ES256 JWT)을 .p8 키로 생성한다.
// Apple은 exp를 최대 6개월까지 허용하지만, 호출 직전에 만들어 바로 쓰므로 5분으로 짧게 둔다
class AppleClientSecretGenerator(
    private val teamId: String,
    private val keyId: String,
    privateKeyPem: String,
    private val clientId: String,
) {
    private val signer = ECDSASigner(parsePrivateKey(privateKeyPem))

    // iss=Team ID, sub=client_id(Bundle ID), aud=Apple, kid=Key ID로 서명한 JWT를 만든다
    fun generate(now: Instant = Instant.now()): String {
        val claims = JWTClaimsSet.Builder()
            .issuer(teamId)
            .subject(clientId)
            .audience(AUDIENCE)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(300)))
            .build()
        val jwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.ES256).keyID(keyId).build(), claims)
        jwt.sign(signer)
        return jwt.serialize()
    }

    companion object {
        const val AUDIENCE = "https://appleid.apple.com"

        // .p8(PKCS#8 PEM)을 EC 개인키로 읽는다. 환경변수로 넘길 때 줄바꿈이 리터럴 "\n"이 되는 경우도 허용한다
        private fun parsePrivateKey(pem: String): ECPrivateKey {
            val base64 = pem.replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace(Regex("\\s"), "")
            return KeyFactory.getInstance("EC")
                .generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64))) as ECPrivateKey
        }
    }
}
