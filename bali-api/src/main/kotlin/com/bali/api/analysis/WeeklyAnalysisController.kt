package com.bali.api.analysis

import com.bali.core.analysis.WeeklyAnalysisRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

// 주간 분석 결과 조회 API 엔드포인트를 처리하는 REST 컨트롤러
@RestController
@RequestMapping("/api/v1/analysis/weekly")
@Tag(name = "WeeklyAnalysis", description = "주간 운동 분석 결과 조회 API")
class WeeklyAnalysisController(
    private val analysisRepository: WeeklyAnalysisRepository,
) {

    // 내 전체 주간 분석 결과 목록 조회 (weekOf 내림차순)
    @Operation(summary = "주간 분석 목록 조회", description = "본인의 전체 주간 분석 결과를 weekOf 내림차순으로 조회한다")
    @GetMapping
    fun list(): List<WeeklyAnalysisResponse> =
        analysisRepository.findAllByUserId(currentUserId()).map { WeeklyAnalysisResponse.from(it) }

    // 특정 주 분석 결과 조회. 없거나 다른 유저 소유면 404
    @Operation(summary = "특정 주 분석 결과 조회", description = "weekOf(해당 주 월요일)로 분석 결과를 조회한다. 없거나 다른 유저 소유면 404")
    @GetMapping("/{weekOf}")
    fun get(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) weekOf: LocalDate): ResponseEntity<WeeklyAnalysisResponse> {
        val analysis = analysisRepository.findByUserIdAndWeekOf(currentUserId(), weekOf) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(WeeklyAnalysisResponse.from(analysis))
    }

    // SecurityContextHolder에서 현재 인증된 사용자의 UUID를 추출
    private fun currentUserId(): UUID =
        UUID.fromString(SecurityContextHolder.getContext().authentication.name)
}
