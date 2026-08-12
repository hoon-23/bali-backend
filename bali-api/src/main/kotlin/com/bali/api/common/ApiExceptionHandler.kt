package com.bali.api.common

import com.bali.api.auth.social.SocialProviderUnavailableException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

// 컨트롤러 전역에서 도메인 검증 실패를 400 JSON 응답으로 변환
@RestControllerAdvice
class ApiExceptionHandler {

    // Exercise 등 도메인 모델의 init 블록에서 발생하는 검증 실패를 400으로 매핑
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<Map<String, String?>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to ex.message))

    // @Valid로 표시된 요청 바디의 Bean Validation 실패(예: @NotBlank, @Size)를 400으로 매핑
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(ex: MethodArgumentNotValidException): ResponseEntity<Map<String, String?>> {
        val firstError = ex.bindingResult.fieldErrors.firstOrNull()
        val message = firstError?.let { "${it.field}: ${it.defaultMessage}" } ?: ex.message
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to message))
    }

    // 요청 바디 JSON이 파싱 불가하거나(문법 오류) enum 등 필드 타입에 맞지 않는 값일 때 400으로 매핑.
    // 여기서 직접 처리하지 않으면 Spring 기본 처리(response.sendError -> "/error" 내부 재전송)로
    // 빠지는데, "/error"가 SecurityConfig의 permitAll 대상이 아니라 인증 없는 요청은 401로
    // 잘못 가려지므로 이 핸들러가 그 경로 자체를 막는다
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadable(ex: HttpMessageNotReadableException): ResponseEntity<Map<String, String?>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "요청 본문을 읽을 수 없습니다"))

    // 소셜 로그인 provider의 5xx/연결 실패를 클라이언트 토큰 문제(400)가 아닌 upstream 장애(502)로 매핑
    @ExceptionHandler(SocialProviderUnavailableException::class)
    fun handleSocialProviderUnavailable(ex: SocialProviderUnavailableException): ResponseEntity<Map<String, String?>> =
        ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(mapOf("error" to ex.message))
}
