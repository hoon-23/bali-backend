package com.bali.api.auth.social

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.time.Duration

class CachingJwkSetSupplierTest {

    private val jwksUri = "https://example-issuer.test/jwks"
    private val rsaKey = RSAKeyGenerator(2048).keyID("test-kid").generate()
    private val jwksJson = JWKSet(rsaKey.toPublicJWK()).toString()

    // 실제 RestClient + MockRestServiceServer로 HTTP 왕복을 흉내낸다(이 저장소는 Mockito를 쓰지 않는
    // 컨벤션이라 deep-stub mock 대신 이 방식을 쓴다)
    private fun mockedClient(): Pair<RestClient, MockRestServiceServer> {
        val builder = RestClient.builder()
        val mockServer = MockRestServiceServer.bindTo(builder).build()
        return builder.build() to mockServer
    }

    @Test
    fun `TTL 이내에는 캐시된 값을 반환하고 다시 fetch하지 않는다`() {
        val (restClient, mockServer) = mockedClient()
        mockServer.expect(requestTo(jwksUri)).andRespond(withSuccess(jwksJson, MediaType.APPLICATION_JSON))
        val supplier = CachingJwkSetSupplier(jwksUri, restClient, ttl = Duration.ofMinutes(10))

        supplier.get()
        supplier.get()

        mockServer.verify()
    }

    @Test
    fun `getFresh는 TTL 이내여도 강제로 새로 fetch한다`() {
        val (restClient, mockServer) = mockedClient()
        mockServer.expect(requestTo(jwksUri)).andRespond(withSuccess(jwksJson, MediaType.APPLICATION_JSON))
        mockServer.expect(requestTo(jwksUri)).andRespond(withSuccess(jwksJson, MediaType.APPLICATION_JSON))
        // 최소 갱신 간격 floor가 이 테스트를 방해하지 않도록 0으로 둔다(floor 자체는 별도 테스트로 검증)
        val supplier = CachingJwkSetSupplier(
            jwksUri,
            restClient,
            ttl = Duration.ofMinutes(10),
            minRefreshInterval = Duration.ZERO,
        )

        supplier.get()
        supplier.getFresh()

        mockServer.verify()
    }

    @Test
    fun `getFresh를 최소 간격 이내에 연속 호출하면 한 번만 fetch한다`() {
        val (restClient, mockServer) = mockedClient()
        mockServer.expect(requestTo(jwksUri)).andRespond(withSuccess(jwksJson, MediaType.APPLICATION_JSON))
        val supplier = CachingJwkSetSupplier(
            jwksUri,
            restClient,
            ttl = Duration.ofMinutes(10),
            minRefreshInterval = Duration.ofMinutes(1),
        )

        supplier.getFresh()
        supplier.getFresh()

        mockServer.verify()
    }

    @Test
    fun `JWKS 응답이 비어있으면 IllegalArgumentException을 던진다`() {
        val (restClient, mockServer) = mockedClient()
        mockServer.expect(requestTo(jwksUri)).andRespond(withSuccess("", MediaType.APPLICATION_JSON))
        val supplier = CachingJwkSetSupplier(jwksUri, restClient)

        assertThrows(IllegalArgumentException::class.java) { supplier.get() }
    }

    @Test
    fun `JWKS 응답이 잘못된 형식이면 IllegalArgumentException을 던진다`() {
        val (restClient, mockServer) = mockedClient()
        mockServer.expect(requestTo(jwksUri)).andRespond(withSuccess("not-valid-json", MediaType.APPLICATION_JSON))
        val supplier = CachingJwkSetSupplier(jwksUri, restClient)

        assertThrows(IllegalArgumentException::class.java) { supplier.get() }
    }

    // provider 장애(5xx)로 새로 fetch가 실패했지만 이전에 정상적으로 캐시해둔 값이 있으면, 예외를
    // 던지지 않고 그 stale 값을 그대로 서빙해야 한다(최종 리뷰: provider 일시 장애가 로그인 전체를
    // 막으면 안 된다는 지적에 대한 수정)
    @Test
    fun `캐시된 값이 있을 때 재조회가 실패하면 예외 대신 stale 캐시를 그대로 반환한다`() {
        val (restClient, mockServer) = mockedClient()
        mockServer.expect(requestTo(jwksUri)).andRespond(withSuccess(jwksJson, MediaType.APPLICATION_JSON))
        mockServer.expect(requestTo(jwksUri)).andRespond(withServerError())
        // ttl=0으로 둬서 두 번째 get() 호출이 캐시를 만료된 것으로 보고 실제로 재조회를 시도하게 만든다
        val supplier = CachingJwkSetSupplier(jwksUri, restClient, ttl = Duration.ZERO)

        val first = supplier.get()
        val second = supplier.get()

        assertSame(first, second)
        mockServer.verify()
    }

    // 캐시가 아예 없는 최초 fetch가 provider 장애(5xx)로 실패하면 폴백할 stale 값이 없으므로
    // 그대로 예외를 던져야 한다
    @Test
    fun `캐시된 값이 전혀 없는데 fetch가 실패하면 예외를 던진다`() {
        val (restClient, mockServer) = mockedClient()
        mockServer.expect(requestTo(jwksUri)).andRespond(withServerError())
        val supplier = CachingJwkSetSupplier(jwksUri, restClient)

        assertThrows(IllegalArgumentException::class.java) { supplier.get() }
    }
}
