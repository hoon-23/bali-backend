package com.bali.api.auth.social

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.web.client.RestClient
import java.time.Duration

class CachingJwkSetSupplierTest {

    private val jwksUri = "https://example-issuer.test/jwks"
    private val rsaKey = RSAKeyGenerator(2048).keyID("test-kid").generate()
    private val jwksJson = JWKSet(rsaKey.toPublicJWK()).toString()

    // RestClient의 fluent 체인(get().uri().retrieve())까지는 deep stub으로 흉내내고,
    // 마지막 body() 호출만 직접 stub/검증할 수 있도록 ResponseSpec mock을 함께 돌려준다
    private fun mockRestClient(responseBody: String?): Pair<RestClient, RestClient.ResponseSpec> {
        val restClient = mock(RestClient::class.java, Mockito.RETURNS_DEEP_STUBS)
        val responseSpec = restClient.get().uri(jwksUri).retrieve()
        `when`(responseSpec.body(String::class.java)).thenReturn(responseBody)
        return restClient to responseSpec
    }

    @Test
    fun `TTL 이내에는 캐시된 값을 반환하고 다시 fetch하지 않는다`() {
        val (restClient, responseSpec) = mockRestClient(jwksJson)
        val supplier = CachingJwkSetSupplier(jwksUri, restClient, ttl = Duration.ofMinutes(10))

        supplier.get()
        supplier.get()

        Mockito.verify(responseSpec, Mockito.times(1)).body(String::class.java)
    }

    @Test
    fun `getFresh는 TTL 이내여도 강제로 새로 fetch한다`() {
        val (restClient, responseSpec) = mockRestClient(jwksJson)
        // 최소 갱신 간격 floor가 이 테스트를 방해하지 않도록 0으로 둔다(floor 자체는 별도 테스트로 검증)
        val supplier = CachingJwkSetSupplier(
            jwksUri,
            restClient,
            ttl = Duration.ofMinutes(10),
            minRefreshInterval = Duration.ZERO,
        )

        supplier.get()
        supplier.getFresh()

        Mockito.verify(responseSpec, Mockito.times(2)).body(String::class.java)
    }

    @Test
    fun `getFresh를 최소 간격 이내에 연속 호출하면 한 번만 fetch한다`() {
        val (restClient, responseSpec) = mockRestClient(jwksJson)
        val supplier = CachingJwkSetSupplier(
            jwksUri,
            restClient,
            ttl = Duration.ofMinutes(10),
            minRefreshInterval = Duration.ofMinutes(1),
        )

        supplier.getFresh()
        supplier.getFresh()

        Mockito.verify(responseSpec, Mockito.times(1)).body(String::class.java)
    }

    @Test
    fun `JWKS 응답이 비어있으면 IllegalArgumentException을 던진다`() {
        val (restClient, _) = mockRestClient(null)
        val supplier = CachingJwkSetSupplier(jwksUri, restClient)

        assertThrows(IllegalArgumentException::class.java) { supplier.get() }
    }

    @Test
    fun `JWKS 응답이 잘못된 형식이면 IllegalArgumentException을 던진다`() {
        val (restClient, _) = mockRestClient("not-valid-json")
        val supplier = CachingJwkSetSupplier(jwksUri, restClient)

        assertThrows(IllegalArgumentException::class.java) { supplier.get() }
    }
}
