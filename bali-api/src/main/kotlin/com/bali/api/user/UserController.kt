package com.bali.api.user

import com.bali.api.auth.currentUserId
import com.bali.api.auth.social.AppleAccountService
import com.bali.core.auth.RefreshTokenRepository
import com.bali.core.notification.DeviceTokenRepository
import com.bali.core.session.WorkoutRecordPolicy
import com.bali.core.user.AuthProvider
import com.bali.core.session.WorkoutSessionRepository
import com.bali.core.user.UserLevel
import com.bali.core.user.UserRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
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
    private val refreshTokenRepository: RefreshTokenRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val appleAccountService: AppleAccountService,
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
        return ResponseEntity.ok(UserResponse.from(user, weeklyWorkoutDays(user.id!!), level(user.id!!)))
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
        return ResponseEntity.ok(UserResponse.from(updated, weeklyWorkoutDays(updated.id!!), level(updated.id!!)))
    }

    // 인증된 사용자의 계정을 탈퇴 처리하는 엔드포인트
    @Operation(summary = "회원 탈퇴", description = "인증된 사용자 본인의 계정을 WITHDRAWN 상태로 변경하고 refresh token 폐기, 기기 푸시 토큰 삭제를 함께 수행한다")
    @Transactional
    @DeleteMapping("/me")
    fun withdraw(): ResponseEntity<Void> {
        val user = userRepository.findById(currentUserId())
            ?: return ResponseEntity.notFound().build()
        // Apple 연동 revoke는 providerId가 파기되기 전에 한다 (저장된 토큰은 userId 기준이라 순서 영향은 없지만 의도를 분명히 함)
        if (user.provider == AuthProvider.APPLE) appleAccountService.revokeOnWithdraw(user.id!!)
        // 사용자 상태를 WITHDRAWN으로 변경하고 저장
        userRepository.save(user.withdraw())
        // 탈퇴 후 토큰 갱신과 푸시 수신이 계속되지 않도록 자격 증명을 함께 정리
        refreshTokenRepository.revokeAllByUserId(user.id!!)
        deviceTokenRepository.deleteAllByUserId(user.id!!)
        return ResponseEntity.noContent().build()
    }

    // 경험치 인정 세션 이력으로 레벨/XP를 요청 시점에 계산
    private fun level(userId: UUID): UserLevel =
        UserLevel.fromSessionCounts(
            sessionRepository.countQualifiedSessionsByDate(userId, WorkoutRecordPolicy.STRICT_FROM),
            sessionRepository.countPartialSessionsByDate(
                userId, UserLevel.PARTIAL_QUALIFICATION_FROM, UserLevel.PARTIAL_MIN_SETS, UserLevel.PARTIAL_MIN_CARDIO_SECONDS,
            ),
        )

    // 이번 주(월~일, 한국 기준) 중 완료된 운동 기록이 있는 날짜 수를 계산
    private fun weeklyWorkoutDays(userId: UUID): Int {
        val today = LocalDate.now(APP_ZONE)
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return sessionRepository.findActiveDates(userId, weekStart, WorkoutRecordPolicy.STRICT_FROM).count { !it.isAfter(today) }
    }
}
