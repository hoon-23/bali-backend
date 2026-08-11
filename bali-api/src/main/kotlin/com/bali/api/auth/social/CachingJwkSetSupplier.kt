package com.bali.api.auth.social

import com.nimbusds.jose.jwk.JWKSet
import org.springframework.web.client.RestClient
import java.time.Duration
import java.time.Instant

// JWKS를 원격 URL에서 가져와 일정 시간 캐싱하는 공급자. 매 로그인 요청마다 네트워크를 타지 않기 위함
class CachingJwkSetSupplier(
    private val jwksUri: String,
    private val restClient: RestClient,
    private val ttl: Duration = Duration.ofHours(1),
) : JwkSetSupplier {
    @Volatile
    private var cached: JWKSet? = null

    @Volatile
    private var cachedAt: Instant = Instant.MIN

    // 캐시가 비어있거나 만료됐으면 새로 fetch하고, 아니면 캐시된 값을 그대로 반환
    override fun get(): JWKSet {
        val now = Instant.now()
        val current = cached
        if (current != null && Duration.between(cachedAt, now) < ttl) {
            return current
        }

        val json = restClient.get().uri(jwksUri).retrieve().body(String::class.java)
            ?: throw IllegalStateException("JWKS 응답이 비어있습니다: $jwksUri")
        val fresh = JWKSet.parse(json)
        cached = fresh
        cachedAt = now
        return fresh
    }
}
