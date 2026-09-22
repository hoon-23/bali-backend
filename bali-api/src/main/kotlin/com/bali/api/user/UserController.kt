package com.bali.api.user

import com.bali.api.auth.currentUserId
import com.bali.core.session.WorkoutSessionRepository
import com.bali.core.user.UserRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
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
        // 서버 실행 환경(JVM 기본 타임존)에 관계없이 이번 주 운동일수 계산을 한국 기준으로 고정
        private val APP_ZONE = ZoneId.of("Asia/Seoul")
    }

    // 인증된 사용자의 정보를 조회하는 엔드포인트
    @Operation(summary = "내 정보 조회", description = "인증된 사용자 본인의 정보를 조회한다")
    @GetMapping("/me")
    fun getMe(): ResponseEntity<UserResponse> {
        val user = userRepository.findById(currentUserId())
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(UserResponse.from(user, weeklyWorkoutDays(user.id!!)))
    }

    // 인증된 사용자의 프로필(닉네임/주간 목표 운동 횟수/이메일)을 부분 수정하는 엔드포인트
    @Operation(summary = "프로필 수정", description = "닉네임/주간 목표 운동 횟수/이메일을 부분 수정한다. null 필드는 기존 값 유지. 이메일은 다른 사용자와 중복되면 400")
    @PatchMapping("/me")
    fun updateProfile(@RequestBody request: UserUpdateRequest): ResponseEntity<UserResponse> {
        val user = userRepository.findById(currentUserId())
            ?: return ResponseEntity.notFound().build()
        request.email?.let { newEmail ->
            val owner = userRepository.findByEmail(newEmail)
            require(owner == null || owner.id == user.id) { "이미 사용 중인 이메일입니다" }
        }
        val updated = userRepository.save(user.updateProfile(request.nickname, request.weeklyGoalSessions, request.email))
        return ResponseEntity.ok(UserResponse.from(updated, weeklyWorkoutDays(updated.id!!)))
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

    // 이번 주(월~일, 한국 기준) 중 완료된 운동 기록이 있는 날짜 수를 계산
    private fun weeklyWorkoutDays(userId: UUID): Int {
        val today = LocalDate.now(APP_ZONE)
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return sessionRepository.findActiveDates(userId, weekStart).count { !it.isAfter(today) }
    }
}
