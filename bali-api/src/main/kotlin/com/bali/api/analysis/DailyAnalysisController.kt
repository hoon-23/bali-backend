package com.bali.api.analysis

import com.bali.core.analysis.DailyStats
import com.bali.core.analysis.DailyStatsCalculator
import com.bali.core.exercise.ExerciseRepository
import com.bali.core.exercise.findAllByIds
import com.bali.core.session.WorkoutSessionRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

// 일별 운동 집계(캘린더 히트맵/막대그래프용) 및 누적(lifetime) 통계 조회 API 엔드포인트를 처리하는 REST 컨트롤러
@RestController
@RequestMapping("/api/v1/analysis")
@Tag(name = "DailyAnalysis", description = "일별/누적 운동 집계 조회 API")
class DailyAnalysisController(
    private val sessionRepository: WorkoutSessionRepository,
    private val exerciseRepository: ExerciseRepository,
) {
    companion object {
        private val APP_ZONE = ZoneId.of("Asia/Seoul")
        private const val MAX_RANGE_DAYS = 366L

        // 누적(lifetime) 조회 시 사용하는 사실상 무제한 시작일
        private val LIFETIME_FROM = LocalDate.of(1970, 1, 1)
    }

    // from~to(inclusive) 구간의 일별 집계 조회. 구간은 최대 1년(366일), 기록 없는 날짜는 응답에서 생략된다
    @Operation(
        summary = "일별 집계 조회",
        description = "from~to(inclusive) 구간의 일별 운동시간/세션수/완료세트수를 조회한다. 최대 1년, 기록 없는 날짜는 생략된다",
    )
    @GetMapping("/daily")
    fun daily(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): List<DailyStatsResponse> {
        require(!to.isBefore(from)) { "to는 from보다 앞설 수 없다" }
        require(ChronoUnit.DAYS.between(from, to) <= MAX_RANGE_DAYS) { "from~to 구간은 최대 ${MAX_RANGE_DAYS}일(1년)까지 조회할 수 있다" }

        return calculateDailyStats(currentUserId(), from, to).map { DailyStatsResponse.from(it) }
    }

    // 가입 이후 전체 기간의 누적 운동일수/운동시간 조회
    @Operation(summary = "누적 통계 조회", description = "가입 이후 전체 기간의 누적 운동일수/운동시간을 요청 시점에 즉석 계산한다")
    @GetMapping("/lifetime")
    fun lifetime(): LifetimeStatsResponse {
        val dailyStats = calculateDailyStats(currentUserId(), LIFETIME_FROM, LocalDate.now(APP_ZONE))
        return LifetimeStatsResponse(
            totalWorkoutDays = dailyStats.size,
            totalWorkoutMinutes = dailyStats.sumOf { it.totalMinutes },
        )
    }

    // 기간 내 세션+로그를 조회해 DailyStatsCalculator로 일별 집계를 계산
    private fun calculateDailyStats(userId: UUID, from: LocalDate, to: LocalDate): List<DailyStats> {
        val sessions = sessionRepository.findAllByUserId(userId, from, to)
        val logs = sessions.flatMap { it.logs }
        val exercisesById = exerciseRepository.findAllByIds(logs.map { it.exerciseId })
        return DailyStatsCalculator.calculate(sessions, exercisesById)
    }

    // SecurityContextHolder에서 현재 인증된 사용자의 UUID를 추출
    private fun currentUserId(): UUID =
        UUID.fromString(SecurityContextHolder.getContext().authentication.name)
}
