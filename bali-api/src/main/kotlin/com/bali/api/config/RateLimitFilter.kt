package com.bali.api.config

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

// /api/v1/auth/** 요청을 클라이언트 IP 기준 분당 10회로 제한 (무차별 로그인/리프레시 시도 방지).
// 인스턴스 1대 운영을 전제로 한 인메모리 구현 — 여러 인스턴스로 스케일하면 Redis 등 분산 백엔드로 교체해야 한다.
// trustedProxyHops: 앞단 프록시(CloudFront→ALB 등)가 X-Forwarded-For에 덧붙이는 항목 수. 0이면 remoteAddr를 그대로 쓴다.
@Component
class RateLimitFilter(
    @Value("\${bali.rate-limit.trusted-proxy-hops:0}") private val trustedProxyHops: Int = 0,
) : OncePerRequestFilter() {

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

        evictIdleBucketsIfTooMany()
        val bucket = buckets.computeIfAbsent(clientIp(request)) { newBucket() }
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

    // 신뢰하는 프록시 홉 수만큼 뒤에서 센 X-Forwarded-For 항목이 실제 클라이언트 IP다.
    // 앞쪽 항목은 클라이언트가 위조할 수 있으므로 절대 쓰지 않는다. 항목이 모자라면 remoteAddr로 되돌린다.
    private fun clientIp(request: HttpServletRequest): String {
        if (trustedProxyHops <= 0) return request.remoteAddr
        val chain = request.getHeader("X-Forwarded-For")?.split(",")?.map { it.trim() }.orEmpty()
        return chain.getOrNull(chain.size - trustedProxyHops)?.takeIf { it.isNotEmpty() } ?: request.remoteAddr
    }

    // 맵이 무한히 커지지 않도록, 상한을 넘으면 토큰이 가득 찬(=최근 요청 없는) 버킷부터 제거한다
    private fun evictIdleBucketsIfTooMany() {
        if (buckets.size <= MAX_BUCKETS) return
        buckets.entries.removeIf { it.value.availableTokens >= REQUESTS_PER_MINUTE }
    }

    companion object {
        private const val REQUESTS_PER_MINUTE = 10L
        private const val MAX_BUCKETS = 10_000
    }
}
