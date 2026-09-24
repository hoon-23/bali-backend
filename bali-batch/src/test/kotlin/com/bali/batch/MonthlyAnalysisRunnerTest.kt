package com.bali.batch

import com.bali.core.analysis.AnalysisStatus
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
import com.bali.core.user.User
import com.bali.core.user.UserRepository
import com.bali.core.user.UserStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

// WeeklyAnalysisRunnerTest와 동일한 구조 — MonthlyAnalysisRunner만 테스트가 없던 공백을 메운다
@SpringBootTest(classes = [BaliBatchApplication::class])
@Transactional
class MonthlyAnalysisRunnerTest {

    @Autowired lateinit var runner: MonthlyAnalysisRunner
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var exerciseRepository: ExerciseRepository
    @Autowired lateinit var sessionRepository: WorkoutSessionRepository
    @Autowired lateinit var analysisRepository: MonthlyAnalysisRepository

    private fun newUser() = userRepository.save(
        User(id = null, email = "batch-monthly-${System.nanoTime()}@example.com", provider = AuthProvider.GOOGLE, providerId = "sub-batch-monthly-${System.nanoTime()}", status = UserStatus.ACTIVE, createdAt = Instant.now())
    )

    private fun savedStrengthExerciseId() = exerciseRepository.save(
        Exercise(id = null, name = "배치월간테스트벤치프레스", variant = null, muscleGroup = MuscleGroup.CHEST, type = ExerciseType.STRENGTH, scope = ExerciseScope.GLOBAL, ownerId = null)
    ).id!!

    // 직전 완료 달(러너가 집계 대상으로 삼는 달)의 아무 날짜
    private fun aDayLastMonth(): LocalDate = LocalDate.now().withDayOfMonth(1).minusMonths(1).plusDays(1)

    private fun lastMonthOf(): LocalDate = LocalDate.now().withDayOfMonth(1).minusMonths(1)

    @Test
    fun `세션이 없는 유저는 NO_ACTIVITY로 저장된다`() {
        val user = newUser()

        runner.run()

        val analysis = analysisRepository.findByUserIdAndMonthOf(user.id!!, lastMonthOf())
        assertEquals(AnalysisStatus.NO_ACTIVITY, analysis?.status)
    }

    // 이전엔 존재하지 않는 exerciseId를 참조하는 세션으로 FAILED 처리도 같이 검증했으나,
    // session_logs.exercise_id에 FK가 걸리면서(V23) 그런 상태 자체를 더는 만들 수 없어 제거함 —
    // onFailure 경로(FAILED 기록)는 여전히 코드에 남아있지만 이 시나리오로는 도달 불가능해졌다.
    @Test
    fun `세션이 있는 유저는 SUCCESS로 집계 저장된다`() {
        val goodUser = newUser()
        val exerciseId = savedStrengthExerciseId()
        val goodLog = SessionLog.create(ExerciseType.STRENGTH, exerciseId, sortOrder = 0, targetSets = 3, targetReps = 10, targetWeight = BigDecimal("60.0"))
            .copy(completed = true, actualSets = 3, actualReps = 10, actualWeight = BigDecimal("60.0"))
        sessionRepository.save(WorkoutSession(id = null, userId = goodUser.id!!, date = aDayLastMonth(), templateId = null, status = SessionStatus.SCHEDULED, logs = listOf(goodLog)))

        val exitCode = runner.run()

        val goodAnalysis = analysisRepository.findByUserIdAndMonthOf(goodUser.id!!, lastMonthOf())

        assertEquals(AnalysisStatus.SUCCESS, goodAnalysis?.status)
        assertEquals(BigDecimal("1800.0"), goodAnalysis?.summary?.volumeByExercise?.get(exerciseId))
        assertEquals(0, exitCode)
    }
}
