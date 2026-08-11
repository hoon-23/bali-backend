package com.bali.api.auth.social

import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.text.ParseException
import java.util.Date

// provider의 JWKS(서명 검증용 공개키 목록)를 제공하는 포트. 실전 구현은 네트워크에서 가져오고,
// 테스트는 로컬에서 생성한 키를 그대로 준다
fun interface JwkSetSupplier {
    fun get(): JWKSet

    // 캐시를 무시하고 강제로 새로 가져온다. 키 로테이션으로 kid를 못 찾았을 때의 폴백용. 기본 구현은 get()과 동일
    fun getFresh(): JWKSet = get()
}

// Google/Apple 공통: OIDC ID Token(JWT)의 서명과 iss/aud/exp를 검증해 클레임을 돌려주는 유틸
class OidcIdTokenVerifier(
    private val jwkSetSupplier: JwkSetSupplier,
    private val expectedIssuer: String,
    private val expectedAudience: String,
) {
    // 서명/발급자/대상/만료를 모두 검증하고, 통과하면 클레임 집합을 반환. 실패하면 IllegalArgumentException
    fun verify(token: String): JWTClaimsSet {
        val signedJwt = try {
            SignedJWT.parse(token)
        } catch (ex: ParseException) {
            throw IllegalArgumentException("유효하지 않은 토큰 형식입니다", ex)
        }

        // 헤더의 kid로 서명에 쓰인 공개키를 JWKS에서 찾는다. 캐시에 없으면 키 로테이션을 의심해
        // 한 번 강제로 새로고침한 뒤 다시 찾는다
        val kid = signedJwt.header.keyID
            ?: throw IllegalArgumentException("토큰 헤더에 kid가 없습니다")
        val cachedSet = jwkSetSupplier.get()
        val matchedKey = cachedSet.getKeyByKeyId(kid) ?: jwkSetSupplier.getFresh().getKeyByKeyId(kid)
        val jwk = matchedKey as? RSAKey
            ?: throw IllegalArgumentException("일치하는 서명 검증 키를 찾을 수 없습니다")

        // nimbus가 서명 검증/클레임 파싱 중 던지는 체크 예외(JOSEException, ParseException)를
        // 모두 IllegalArgumentException으로 통일한다
        val claims = try {
            if (!signedJwt.verify(RSASSAVerifier(jwk.toRSAPublicKey()))) {
                throw IllegalArgumentException("토큰 서명 검증에 실패했습니다")
            }
            signedJwt.getJWTClaimsSet()
        } catch (ex: JOSEException) {
            throw IllegalArgumentException("토큰 서명 검증에 실패했습니다", ex)
        } catch (ex: ParseException) {
            throw IllegalArgumentException("토큰 클레임을 파싱하는데 실패했습니다", ex)
        }

        if (claims.issuer != expectedIssuer) {
            throw IllegalArgumentException("발급자(iss)가 일치하지 않습니다")
        }
        if (expectedAudience !in claims.audience) {
            throw IllegalArgumentException("대상(aud)이 일치하지 않습니다")
        }
        if (claims.expirationTime == null || claims.expirationTime.before(Date())) {
            throw IllegalArgumentException("만료된 토큰입니다")
        }

        return claims
    }
}
