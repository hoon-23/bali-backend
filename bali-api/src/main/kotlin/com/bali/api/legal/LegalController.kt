package com.bali.api.legal

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// 인증 없이 공개되는 법적 문서(개인정보처리방침 등) 조회 API
@RestController
@RequestMapping("/api/v1/legal")
@Tag(name = "Legal", description = "공개 법적 문서 조회 API")
class LegalController {

    // 정적 문서이므로 요청마다 파일을 다시 읽지 않고 기동 시 한 번만 읽어 캐시한다
    private val privacyPolicyMarkdown: String =
        ClassPathResource("legal/privacy-policy.md").inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }

    // 개인정보처리방침 전문을 raw markdown으로 반환한다 - 프론트가 마크다운 렌더러로 앱 테마에 맞춰 표시한다
    @Operation(summary = "개인정보처리방침 조회", description = "개인정보처리방침 전문을 raw markdown 텍스트로 반환한다")
    @GetMapping("/privacy-policy", produces = ["text/markdown;charset=UTF-8"])
    fun privacyPolicy(): ResponseEntity<String> =
        ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8"))
            .body(privacyPolicyMarkdown)
}
