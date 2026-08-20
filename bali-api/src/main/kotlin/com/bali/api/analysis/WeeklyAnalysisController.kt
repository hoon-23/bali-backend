package com.bali.api.analysis

import com.bali.core.analysis.WeeklyAnalysisRepository
import com.bali.core.analysis.WeeklyStatsCalculator
import com.bali.core.exercise.ExerciseRepository
import com.bali.core.session.SessionStatus
import com.bali.core.session.WorkoutSessionRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID

// 주간 분석 결과 조회 API 엔드포인트를 처리하는 REST 컨트롤러
@RestController
@RequestMapping("/api/v1/analysis/weekly")
@Tag(name = "WeeklyAnalysis", description = "주간 운동 분석 결과 조회 API")
class WeeklyAnalysisController(
    private val analysisRepository: WeeklyAnalysisRepository,
    private val sessionRepository: WorkoutSessionRepository,
    private val exerciseRepository: ExerciseRepository,
) {
    companion object {
        // 서버 실행 환경(JVM 기본 타임존)에 관계없이 "이번 주"를 한국 기준으로 고정
        private val APP_ZONE = ZoneId.of("Asia/Seoul")
    }

    // 내 전체 주간 분석 결과 목록 조회 (weekOf 내림차순)
    @Operation(summary = "주간 분석 목록 조회", description = "본인의 전체 주간 분석 결과를 weekOf 내림차순으로 조회한다")
    @GetMapping
    fun list(): List<WeeklyAnalysisResponse> =
        analysisRepository.findAllByUserId(currentUserId()).map { WeeklyAnalysisResponse.from(it) }

    // 진행 중인 이번 주 실시간 집계 조회. 배치가 아직 처리하지 않은 현재 주 데이터를 요청 시점에 즉석 계산한다
    @Operation(
        summary = "이번 주 실시간 집계 조회",
        description = "진행 중인 이번 주(월~일)의 운동시간/근력·유산소 누적시간/완료 세션 수를 요청 시점에 즉석 계산한다. 저장되지 않는다",
    )
    @GetMapping("/current")
    fun current(): CurrentWeekSummaryResponse {
        val userId = currentUserId()
        val weekOf = LocalDate.now(APP_ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sessions = sessionRepository.findAllByUserId(userId, weekOf, weekOf.plusDays(6))

        val logs = sessions.flatMap { it.logs }
        val exercisesById = logs.map { it.exerciseId }.distinct().associateWith { exerciseId ->
            exerciseRepository.findById(exerciseId) ?: throw IllegalStateException("존재하지 않는 exerciseId: $exerciseId")
        }
        val summary = WeeklyStatsCalculator.calculate(logs, exercisesById, previousSummary = null)
        val completedSessionsCount = sessions.count { it.status == SessionStatus.COMPLETED }

        return CurrentWeekSummaryResponse.from(weekOf, summary, completedSessionsCount)
    }

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
