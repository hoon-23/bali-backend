package com.bali.api.analysis

import com.bali.api.auth.jwt.JwtTokenProvider
import com.bali.core.exercise.Exercise
import com.bali.core.exercise.ExerciseRepository
import com.bali.core.exercise.ExerciseScope
import com.bali.core.exercise.ExerciseType
import com.bali.core.exercise.MuscleGroup
import com.bali.core.session.SessionLog
import com.bali.core.session.SessionStatus
import com.bali.core.session.WorkoutSession
import com.bali.core.session.WorkoutSessionRepository
import com.bali.core.user.AuthProvider
import com.bali.infra.user.UserJpaEntity
import com.bali.infra.user.UserJpaRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DailyAnalysisControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired lateinit var userJpaRepository: UserJpaRepository
    @Autowired lateinit var sessionRepository: WorkoutSessionRepository
    @Autowired lateinit var exerciseRepository: ExerciseRepository

    private fun issueTokenForNewUser(): Pair<String, UUID> {
        val entity = userJpaRepository.save(
            UserJpaEntity(email = "daily-test-${System.nanoTime()}@example.com", provider = AuthProvider.GOOGLE, providerId = "sub-daily-${System.nanoTime()}")
        )
        return jwtTokenProvider.generateToken(entity.id, entity.email) to entity.id
    }

    private fun saveSession(userId: UUID, date: LocalDate, strengthId: UUID, cardioId: UUID) {
        val strengthLog = SessionLog.create(ExerciseType.STRENGTH, strengthId, sortOrder = 0, targetSets = 3, targetReps = 10, targetWeight = BigDecimal("60.0"))
            .copy(completed = true, actualSets = 3, actualReps = 10, actualWeight = BigDecimal("60.0"))
        val cardioLog = SessionLog.create(ExerciseType.CARDIO, cardioId, sortOrder = 1, targetDurationSeconds = 1800)
            .copy(completed = true, actualDurationSeconds = 1800)
        sessionRepository.save(
            WorkoutSession(id = null, userId = userId, date = date, templateId = null, status = SessionStatus.COMPLETED, logs = listOf(strengthLog, cardioLog))
        )
    }

    @Test
    fun `GET analysis daily는 구간 내 일별 집계를 반환한다`() {
        val (token, userId) = issueTokenForNewUser()
        val strengthId = exerciseRepository.save(
            Exercise(id = null, name = "일별집계벤치프레스", variant = null, muscleGroup = MuscleGroup.CHEST, type = ExerciseType.STRENGTH, scope = ExerciseScope.GLOBAL, ownerId = null)
        ).id!!
        val cardioId = exerciseRepository.save(
            Exercise(id = null, name = "일별집계러닝", variant = null, muscleGroup = MuscleGroup.CARDIO, type = ExerciseType.CARDIO, scope = ExerciseScope.GLOBAL, ownerId = null)
        ).id!!
        saveSession(userId, LocalDate.of(2026, 8, 3), strengthId, cardioId)

        mockMvc.perform(
            get("/api/v1/analysis/daily")
                .param("from", "2026-08-01")
                .param("to", "2026-08-07")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].date").value("2026-08-03"))
            .andExpect(jsonPath("$[0].totalMinutes").value(42))
            .andExpect(jsonPath("$[0].sessionsCount").value(1))
            .andExpect(jsonPath("$[0].completedSets").value(4))
    }

    @Test
    fun `GET analysis daily는 기록 없는 날짜를 생략한다`() {
        val (token, _) = issueTokenForNewUser()

        mockMvc.perform(
            get("/api/v1/analysis/daily")
                .param("from", "2026-08-01")
                .param("to", "2026-08-07")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `GET analysis daily는 to가 from보다 앞서면 400을 반환한다`() {
        val (token, _) = issueTokenForNewUser()

        mockMvc.perform(
            get("/api/v1/analysis/daily")
                .param("from", "2026-08-07")
                .param("to", "2026-08-01")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET analysis daily는 구간이 1년을 초과하면 400을 반환한다`() {
        val (token, _) = issueTokenForNewUser()

        mockMvc.perform(
            get("/api/v1/analysis/daily")
                .param("from", "2024-01-01")
                .param("to", "2026-01-02")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET analysis lifetime은 가입 이후 누적 운동일수와 시간을 반환한다`() {
        val (token, userId) = issueTokenForNewUser()
        val strengthId = exerciseRepository.save(
            Exercise(id = null, name = "누적집계벤치프레스", variant = null, muscleGroup = MuscleGroup.CHEST, type = ExerciseType.STRENGTH, scope = ExerciseScope.GLOBAL, ownerId = null)
        ).id!!
        val cardioId = exerciseRepository.save(
            Exercise(id = null, name = "누적집계러닝", variant = null, muscleGroup = MuscleGroup.CARDIO, type = ExerciseType.CARDIO, scope = ExerciseScope.GLOBAL, ownerId = null)
        ).id!!
        saveSession(userId, LocalDate.of(2026, 1, 3), strengthId, cardioId)
        saveSession(userId, LocalDate.of(2026, 8, 3), strengthId, cardioId)

        mockMvc.perform(get("/api/v1/analysis/lifetime").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalWorkoutDays").value(2))
            .andExpect(jsonPath("$.totalWorkoutMinutes").value(84))
    }

    @Test
    fun `GET analysis lifetime은 기록이 없으면 0을 반환한다`() {
        val (token, _) = issueTokenForNewUser()

        mockMvc.perform(get("/api/v1/analysis/lifetime").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalWorkoutDays").value(0))
            .andExpect(jsonPath("$.totalWorkoutMinutes").value(0))
    }
}
