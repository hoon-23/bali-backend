package com.bali.api.auth.social

// provider가 5xx를 반환했거나 연결/응답 자체가 실패해서, 클라이언트가 보낸 토큰이 진짜 무효한
// 것인지 provider 장애인지 구분할 수 없는 상황을 나타낸다. IllegalArgumentException(토큰 검증
// 실패, 400)과 의미가 다르므로 별도 타입으로 둬 ApiExceptionHandler가 502로 구분 응답하게 한다
class SocialProviderUnavailableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
