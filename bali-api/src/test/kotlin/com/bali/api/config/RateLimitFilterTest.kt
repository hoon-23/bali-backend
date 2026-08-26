package com.bali.api.config

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

// /api/v1/auth/** 요청을 IP당 분당 10회로 제한하는 필터의 동작을 검증
class RateLimitFilterTest {

    private val filter = RateLimitFilter()

    private fun authRequest(remoteAddr: String = "1.2.3.4") = MockHttpServletRequest().apply {
        requestURI = "/api/v1/auth/login"
        this.remoteAddr = remoteAddr
    }

    @Test
    fun `분당 10회까지는 통과시킨다`() {
        val response = MockHttpServletResponse()
        val chain = FilterChain { _, res -> (res as MockHttpServletResponse).status = 200 }

        repeat(10) {
            val res = MockHttpServletResponse()
            filter.doFilter(authRequest(), res, chain)
            assertEquals(200, res.status)
        }
    }

    @Test
    fun `11번째 요청부터는 429를 반환한다`() {
        val chain = FilterChain { _, res -> (res as MockHttpServletResponse).status = 200 }
        val remoteAddr = "5.6.7.8"
        repeat(10) { filter.doFilter(authRequest(remoteAddr), MockHttpServletResponse(), chain) }

        val res = MockHttpServletResponse()
        filter.doFilter(authRequest(remoteAddr), res, chain)

        assertEquals(429, res.status)
        assertEquals(true, res.getHeader("Retry-After") != null)
    }

    @Test
    fun `IP가 다르면 별도로 카운트한다`() {
        val chain = FilterChain { _, res -> (res as MockHttpServletResponse).status = 200 }
        repeat(10) { filter.doFilter(authRequest("9.9.9.9"), MockHttpServletResponse(), chain) }

        val res = MockHttpServletResponse()
        filter.doFilter(authRequest("10.10.10.10"), res, chain)

        assertEquals(200, res.status)
    }

    @Test
    fun `auth 경로가 아니면 제한 없이 통과시킨다`() {
        val chain = FilterChain { _, res -> (res as MockHttpServletResponse).status = 200 }
        val request = MockHttpServletRequest().apply {
            requestURI = "/api/v1/templates"
            remoteAddr = "1.1.1.1"
        }

        repeat(15) {
            val res = MockHttpServletResponse()
            filter.doFilter(request, res, chain)
            assertEquals(200, res.status)
        }
    }
}
