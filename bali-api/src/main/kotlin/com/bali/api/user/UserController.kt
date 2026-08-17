package com.bali.api.user

import com.bali.core.session.WorkoutSessionRepository
import com.bali.core.user.StreakCalculator
import com.bali.core.user.UserRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

// 사용자 관련 API 엔드포인트를 처리하는 REST 컨트롤러
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User", description = "사용자 정보 조회/수정/탈퇴 API")
class UserController(
    private val userRepository: UserRepository,
    private val sessionRepository: WorkoutSessionRepository,
) {
    companion object {
        // 연속운동일 계산 시 조회할 최대 과거 범위 (주 3회 기준으로도 넉넉한 상한선, 쿼리 비용 제한용)
        private const val STREAK_LOOKBACK_DAYS = 400L

        // 서버 실행 환경(JVM 기본 타임존)에 관계없이 연속운동일 계산을 한국 기준으로 고정
        private val APP_ZONE = ZoneId.of("Asia/Seoul")
    }

    // 인증된 사용자의 정보를 조회하는 엔드포인트
    @Operation(summary = "내 정보 조회", description = "인증된 사용자 본인의 정보를 조회한다")
    @GetMapping("/me")
    fun getMe(): ResponseEntity<UserResponse> {
        val user = userRepository.findById(currentUserId())
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(UserResponse.from(user, consecutiveDays(user.id!!)))
    }

    // 인증된 사용자의 프로필(닉네임/주간 목표 운동 횟수)을 부분 수정하는 엔드포인트
    @Operation(summary = "프로필 수정", description = "닉네임/주간 목표 운동 횟수를 부분 수정한다. null 필드는 기존 값 유지")
    @PatchMapping("/me")
    fun updateProfile(@RequestBody request: UserUpdateRequest): ResponseEntity<UserResponse> {
        val user = userRepository.findById(currentUserId())
            ?: return ResponseEntity.notFound().build()
        val updated = userRepository.save(user.updateProfile(request.nickname, request.weeklyGoalSessions))
        return ResponseEntity.ok(UserResponse.from(updated, consecutiveDays(updated.id!!)))
    }

    // 인증된 사용자의 계정을 탈퇴 처리하는 엔드포인트
    @Operation(summary = "회원 탈퇴", description = "인증된 사용자 본인의 계정을 WITHDRAWN 상태로 변경한다")
    @DeleteMapping("/me")
    fun withdraw(): ResponseEntity<Void> {
        val user = userRepository.findById(currentUserId())
            ?: return ResponseEntity.notFound().build()
        // 사용자 상태를 WITHDRAWN으로 변경하고 저장
        userRepository.save(user.withdraw())
        return ResponseEntity.noContent().build()
    }

    // 최근 STREAK_LOOKBACK_DAYS일 내 활동 날짜로부터 연속운동일을 계산
    private fun consecutiveDays(userId: UUID): Int {
        val today = LocalDate.now(APP_ZONE)
        val activeDates = sessionRepository.findActiveDates(userId, today.minusDays(STREAK_LOOKBACK_DAYS))
        return StreakCalculator.calculate(activeDates, today)
    }

    // SecurityContextHolder에서 현재 인증된 사용자의 UUID를 추출
    private fun currentUserId(): UUID =
        UUID.fromString(SecurityContextHolder.getContext().authentication.name)
}
