package com.bali.api.analysis

import com.bali.api.auth.jwt.JwtTokenProvider
import com.bali.core.analysis.AnalysisStatus
import com.bali.core.analysis.AnalysisSummary
import com.bali.core.analysis.Insight
import com.bali.core.analysis.MonthlyAnalysis
import com.bali.core.analysis.MonthlyAnalysisRepository
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
import java.time.ZoneId
import java.util.UUID

// WeeklyAnalysisControllerTest와 동일한 구조 — MonthlyAnalysisController만 테스트가 없던 공백을 메운다
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MonthlyAnalysisControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired lateinit var userJpaRepository: UserJpaRepository
    @Autowired lateinit var analysisRepository: MonthlyAnalysisRepository
    @Autowired lateinit var sessionRepository: WorkoutSessionRepository
    @Autowired lateinit var exerciseRepository: ExerciseRepository

    private fun issueTokenForNewUser(): Pair<String, UUID> {
        val entity = userJpaRepository.save(
            UserJpaEntity(email = "monthly-analysis-test-${System.nanoTime()}@example.com", provider = AuthProvider.GOOGLE, providerId = "sub-monthly-analysis-${System.nanoTime()}")
        )
        return jwtTokenProvider.generateToken(entity.id, entity.email) to entity.id
    }

    private fun successSummary() = AnalysisSummary(
        totalWorkoutMinutes = 60,
        volumeByExercise = emptyMap(),
        volumeByMuscleGroup = mapOf(MuscleGroup.CHEST to BigDecimal("1000.0")),
        cardioTotalMinutes = 0,
        completionRate = BigDecimal("100.0"),
        volumeChangeFromLastWeekPercent = null,
    )

    @Test
    fun `GET analysis monthly는 본인 분석 결과를 monthOf 내림차순으로 반환한다`() {
        val (token, userId) = issueTokenForNewUser()
        analysisRepository.save(
            MonthlyAnalysis(id = null, userId = userId, monthOf = LocalDate.of(2026, 6, 1), status = AnalysisStatus.SUCCESS, summary = successSummary(), insights = listOf(Insight(id = null, summaryText = "완료율이 100%예요")))
        )
        analysisRepository.save(
            MonthlyAnalysis(id = null, userId = userId, monthOf = LocalDate.of(2026, 7, 1), status = AnalysisStatus.NO_ACTIVITY, summary = null, insights = emptyList())
        )

        mockMvc.perform(get("/api/v1/analysis/monthly").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].monthOf").value("2026-07-01"))
            .andExpect(jsonPath("$[1].monthOf").value("2026-06-01"))
            .andExpect(jsonPath("$[1].insights[0]").value("완료율이 100%예요"))
    }

    @Test
    fun `GET analysis monthly current는 진행 중인 이번 달 실시간 집계를 반환한다`() {
        val (token, userId) = issueTokenForNewUser()
        val strengthId = exerciseRepository.save(
            Exercise(id = null, name = "월간실시간집계벤치프레스", variant = null, muscleGroup = MuscleGroup.CHEST, type = ExerciseType.STRENGTH, scope = ExerciseScope.GLOBAL, ownerId = null)
        ).id!!
        val cardioId = exerciseRepository.save(
            Exercise(id = null, name = "월간실시간집계러닝", variant = null, muscleGroup = MuscleGroup.CARDIO, type = ExerciseType.CARDIO, scope = ExerciseScope.GLOBAL, ownerId = null)
        ).id!!
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        val monthOf = today.withDayOfMonth(1)

        val strengthLog = SessionLog.create(ExerciseType.STRENGTH, strengthId, sortOrder = 0, targetSets = 3, targetReps = 10, targetWeight = BigDecimal("60.0"))
            .copy(completed = true, actualSets = 3, actualReps = 10, actualWeight = BigDecimal("60.0"))
        val cardioLog = SessionLog.create(ExerciseType.CARDIO, cardioId, sortOrder = 1, targetDurationSeconds = 1800)
            .copy(completed = true, actualDurationSeconds = 1800)
        sessionRepository.save(
            WorkoutSession(id = null, userId = userId, date = today, templateId = null, status = SessionStatus.COMPLETED, logs = listOf(strengthLog, cardioLog))
        )

        mockMvc.perform(get("/api/v1/analysis/monthly/current").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.monthOf").value(monthOf.toString()))
            .andExpect(jsonPath("$.cardioMinutes").value(30))
            .andExpect(jsonPath("$.strengthMinutes").value(12))
            .andExpect(jsonPath("$.totalWorkoutMinutes").value(42))
            .andExpect(jsonPath("$.completedSessionsCount").value(1))
    }

    @Test
    fun `GET analysis monthly current는 이번 달 기록이 없으면 0으로 채워진 결과를 반환한다`() {
        val (token, _) = issueTokenForNewUser()

        mockMvc.perform(get("/api/v1/analysis/monthly/current").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalWorkoutMinutes").value(0))
            .andExpect(jsonPath("$.strengthMinutes").value(0))
            .andExpect(jsonPath("$.cardioMinutes").value(0))
            .andExpect(jsonPath("$.completedSessionsCount").value(0))
    }

    @Test
    fun `GET analysis monthly by monthOf는 존재하지 않는 달이면 404를 반환한다`() {
        val (token, _) = issueTokenForNewUser()

        mockMvc.perform(get("/api/v1/analysis/monthly/2026-01-01").header("Authorization", "Bearer $token"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `GET analysis monthly by monthOf는 본인 분석 결과를 반환한다`() {
        val (token, userId) = issueTokenForNewUser()
        analysisRepository.save(
            MonthlyAnalysis(id = null, userId = userId, monthOf = LocalDate.of(2026, 6, 1), status = AnalysisStatus.SUCCESS, summary = successSummary(), insights = emptyList())
        )

        mockMvc.perform(get("/api/v1/analysis/monthly/2026-06-01").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.summary.totalWorkoutMinutes").value(60))
    }

    @Test
    fun `GET analysis monthly by monthOf는 다른 유저의 분석 결과를 404로 반환한다`() {
        val (_, ownerId) = issueTokenForNewUser()
        val (otherToken, _) = issueTokenForNewUser()
        analysisRepository.save(
            MonthlyAnalysis(id = null, userId = ownerId, monthOf = LocalDate.of(2026, 6, 1), status = AnalysisStatus.SUCCESS, summary = successSummary(), insights = emptyList())
        )

        mockMvc.perform(get("/api/v1/analysis/monthly/2026-06-01").header("Authorization", "Bearer $otherToken"))
            .andExpect(status().isNotFound)
    }
}
