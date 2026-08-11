package com.bali.api.auth.social

import com.nimbusds.jose.jwk.JWKSet
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.text.ParseException
import java.time.Duration
import java.time.Instant

// JWKS를 원격 URL에서 가져와 일정 시간 캐싱하는 공급자. 매 로그인 요청마다 네트워크를 타지 않기 위함
class CachingJwkSetSupplier(
    private val jwksUri: String,
    private val restClient: RestClient,
    private val ttl: Duration = Duration.ofHours(1),
    // getFresh()가 kid miss마다 매번 fetch하지 않도록 두는 최소 재조회 간격(fetch storm 방지용 floor)
    private val minRefreshInterval: Duration = Duration.ofSeconds(10),
) : JwkSetSupplier {
    @Volatile
    private var cached: JWKSet? = null

    @Volatile
    private var cachedAt: Instant = Instant.MIN

    // 캐시가 비어있거나 TTL이 지났으면 새로 fetch하고, 아니면 캐시된 값을 그대로 반환
    override fun get(): JWKSet {
        val current = cached
        if (current != null && Duration.between(cachedAt, Instant.now()) < ttl) {
            return current
        }
        return fetchAndCache()
    }

    // 캐시를 무시하고 강제로 새로 가져온다(키 로테이션으로 kid를 못 찾았을 때의 폴백). 단, 마지막 갱신으로부터
    // 최소 간격(minRefreshInterval) 이내면 캐시를 그대로 반환해 악의적인 kid로 매 요청마다 fetch를
    // 유발하는 것을 막는다
    override fun getFresh(): JWKSet {
        val current = cached
        if (current != null && Duration.between(cachedAt, Instant.now()) < minRefreshInterval) {
            return current
        }
        return fetchAndCache()
    }

    // 실제 네트워크 호출로 JWKS를 가져와 캐시에 반영한다. 실패 시 예외 타입을 IllegalArgumentException으로
    // 통일해 OidcIdTokenVerifier 등 호출자가 한 가지 예외 타입만 처리하면 되게 한다
    private fun fetchAndCache(): JWKSet {
        val json = try {
            restClient.get().uri(jwksUri).retrieve().body(String::class.java)
                ?: throw IllegalArgumentException("JWKS 응답이 비어있습니다: $jwksUri")
        } catch (ex: RestClientException) {
            throw IllegalArgumentException("JWKS를 가져오는데 실패했습니다: $jwksUri", ex)
        }
        val fresh = try {
            JWKSet.parse(json)
        } catch (ex: ParseException) {
            throw IllegalArgumentException("JWKS 응답을 파싱하는데 실패했습니다: $jwksUri", ex)
        }
        cached = fresh
        cachedAt = Instant.now()
        return fresh
    }
}
