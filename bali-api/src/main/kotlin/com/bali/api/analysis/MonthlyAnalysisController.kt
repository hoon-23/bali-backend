package com.bali.api.analysis

import com.bali.api.auth.currentUserId
import com.bali.core.analysis.MonthlyAnalysisRepository
import com.bali.core.analysis.MonthlyStatsCalculator
import com.bali.core.exercise.ExerciseRepository
import com.bali.core.exercise.findAllByIds
import com.bali.core.session.SessionStatus
import com.bali.core.session.WorkoutSessionRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.ZoneId

// 월간 분석 결과 조회 API 엔드포인트를 처리하는 REST 컨트롤러
@RestController
@RequestMapping("/api/v1/analysis/monthly")
@Tag(name = "MonthlyAnalysis", description = "월간 운동 분석 결과 조회 API")
class MonthlyAnalysisController(
    private val analysisRepository: MonthlyAnalysisRepository,
    private val sessionRepository: WorkoutSessionRepository,
    private val exerciseRepository: ExerciseRepository,
) {
    companion object {
        // 서버 실행 환경(JVM 기본 타임존)에 관계없이 "이번 달"을 한국 기준으로 고정
        private val APP_ZONE = ZoneId.of("Asia/Seoul")
    }

    // 내 전체 월간 분석 결과 목록 조회 (monthOf 내림차순)
    @Operation(summary = "월간 분석 목록 조회", description = "본인의 전체 월간 분석 결과를 monthOf 내림차순으로 조회한다")
    @GetMapping
    fun list(): List<MonthlyAnalysisResponse> =
        analysisRepository.findAllByUserId(currentUserId()).map { MonthlyAnalysisResponse.from(it) }

    // 진행 중인 이번 달 실시간 집계 조회. 배치가 아직 처리하지 않은 현재 달 데이터를 요청 시점에 즉석 계산한다
    @Operation(
        summary = "이번 달 실시간 집계 조회",
        description = "진행 중인 이번 달(1일~말일)의 운동시간/근력·유산소 누적시간/완료 세션 수를 요청 시점에 즉석 계산한다. 저장되지 않는다",
    )
    @GetMapping("/current")
    fun current(): CurrentMonthSummaryResponse {
        val userId = currentUserId()
        val monthOf = LocalDate.now(APP_ZONE).withDayOfMonth(1)
        val sessions = sessionRepository.findAllByUserId(userId, monthOf, monthOf.plusMonths(1).minusDays(1))

        val logs = sessions.flatMap { it.logs }
        val exercisesById = exerciseRepository.findAllByIds(logs.map { it.exerciseId })
        val summary = MonthlyStatsCalculator.calculate(logs, exercisesById, previousSummary = null)
        val completedSessionsCount = sessions.count { it.status == SessionStatus.COMPLETED }

        return CurrentMonthSummaryResponse.from(monthOf, summary, completedSessionsCount)
    }

    // 특정 월 분석 결과 조회. 없거나 다른 유저 소유면 404
    @Operation(summary = "특정 월 분석 결과 조회", description = "monthOf(해당 월 1일)로 분석 결과를 조회한다. 없거나 다른 유저 소유면 404")
    @GetMapping("/{monthOf}")
    fun get(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) monthOf: LocalDate): ResponseEntity<MonthlyAnalysisResponse> {
        val analysis = analysisRepository.findByUserIdAndMonthOf(currentUserId(), monthOf) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(MonthlyAnalysisResponse.from(analysis))
    }
}
