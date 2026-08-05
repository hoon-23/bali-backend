package com.bali.api.common

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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
}
