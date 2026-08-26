package com.bali.api.config

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

// /api/v1/auth/** 요청을 클라이언트 IP 기준 분당 10회로 제한 (무차별 로그인/리프레시 시도 방지).
// 인스턴스 1대 운영을 전제로 한 인메모리 구현 — 여러 인스턴스로 스케일하면 Redis 등 분산 백엔드로 교체해야 한다.
@Component
class RateLimitFilter : OncePerRequestFilter() {

    private val buckets = ConcurrentHashMap<String, Bucket>()

    private fun newBucket(): Bucket = Bucket.builder()
        .addLimit(
            Bandwidth.builder()
                .capacity(REQUESTS_PER_MINUTE)
                .refillGreedy(REQUESTS_PER_MINUTE, Duration.ofMinutes(1))
                .build()
        )
        .build()

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        if (!request.requestURI.startsWith("/api/v1/auth/")) {
            filterChain.doFilter(request, response)
            return
        }

        val bucket = buckets.computeIfAbsent(request.remoteAddr) { newBucket() }
        val probe = bucket.tryConsumeAndReturnRemaining(1)
        if (probe.isConsumed) {
            filterChain.doFilter(request, response)
            return
        }

        val retryAfterSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.nanosToWaitForRefill) + 1
        response.status = 429
        response.contentType = "application/json"
        response.setHeader("Retry-After", retryAfterSeconds.toString())
        response.writer.write("{\"error\": \"요청이 너무 많습니다. 잠시 후 다시 시도해주세요.\"}")
    }

    companion object {
        private const val REQUESTS_PER_MINUTE = 10L
    }
}
