package com.bali.api.legal

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
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

    // 브라우저 직접 열람용 HTML. 마크다운을 기동 시 한 번만 렌더링해 캐시한다
    private val privacyPolicyHtml: String = renderHtmlPage(privacyPolicyMarkdown)

    // 개인정보처리방침 전문을 raw markdown으로 반환한다 - 프론트가 마크다운 렌더러로 앱 테마에 맞춰 표시한다
    @Operation(summary = "개인정보처리방침 조회", description = "개인정보처리방침 전문을 raw markdown 텍스트로 반환한다")
    @GetMapping("/privacy-policy", produces = ["text/markdown;charset=UTF-8"])
    fun privacyPolicy(): ResponseEntity<String> =
        ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8"))
            .body(privacyPolicyMarkdown)

    // 개인정보처리방침을 완성된 HTML 페이지로 반환한다 - App Store Connect 개인정보 처리방침 URL 등록용
    @Operation(summary = "개인정보처리방침 조회(HTML)", description = "개인정보처리방침 전문을 브라우저에서 바로 볼 수 있는 HTML로 반환한다")
    @GetMapping("/privacy-policy.html", produces = ["text/html;charset=UTF-8"])
    fun privacyPolicyHtml(): ResponseEntity<String> =
        ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
            .body(privacyPolicyHtml)

    // 마크다운(표 포함)을 모바일 대응 최소 스타일의 HTML 문서로 변환한다
    private fun renderHtmlPage(markdown: String): String {
        val extensions = listOf(TablesExtension.create())
        val parser = Parser.builder().extensions(extensions).build()
        val body = HtmlRenderer.builder().extensions(extensions).build().render(parser.parse(markdown))
        return """<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Swayt 개인정보처리방침</title>
<style>
body{font-family:-apple-system,BlinkMacSystemFont,"Apple SD Gothic Neo",sans-serif;line-height:1.7;max-width:760px;margin:0 auto;padding:16px;color:#111;background:#fff}
table{border-collapse:collapse;width:100%;display:block;overflow-x:auto}
th,td{border:1px solid #ccc;padding:6px 8px;text-align:left;vertical-align:top}
th{background:#f3f3f3}
@media(prefers-color-scheme:dark){body{color:#eee;background:#111}th{background:#222}th,td{border-color:#444}}
</style>
</head>
<body>
$body
</body>
</html>
"""
    }
}
