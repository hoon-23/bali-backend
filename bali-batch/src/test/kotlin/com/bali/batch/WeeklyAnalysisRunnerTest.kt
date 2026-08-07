package com.bali.batch

import com.bali.core.analysis.AnalysisStatus
import com.bali.core.analysis.WeeklyAnalysisRepository
import com.bali.core.exercise.Exercise
import com.bali.core.exercise.ExerciseRepository
import com.bali.core.exercise.ExerciseScope
import com.bali.core.exercise.ExerciseType
import com.bali.core.exercise.MuscleGroup
import com.bali.core.session.SessionLog
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

@SpringBootTest(classes = [BaliBatchApplication::class])
@Transactional
class WeeklyAnalysisRunnerTest {

    @Autowired lateinit var runner: WeeklyAnalysisRunner
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var exerciseRepository: ExerciseRepository
    @Autowired lateinit var sessionRepository: WorkoutSessionRepository
    @Autowired lateinit var analysisRepository: WeeklyAnalysisRepository

    private fun newUser() = userRepository.save(
        User(id = null, email = "batch-${System.nanoTime()}@example.com", provider = AuthProvider.GOOGLE, providerId = "sub-batch-${System.nanoTime()}", status = UserStatus.ACTIVE, createdAt = Instant.now())
    )

    private fun savedStrengthExerciseId() = exerciseRepository.save(
        Exercise(id = null, name = "배치테스트벤치프레스", variant = null, muscleGroup = MuscleGroup.CHEST, type = ExerciseType.STRENGTH, scope = ExerciseScope.GLOBAL, ownerId = null)
    ).id!!

    // 이번 주(월요일 기준 직전 완료 주)의 아무 날짜
    private fun aDayLastWeek(): LocalDate =
        LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).minusWeeks(1).plusDays(1)

    @Test
    fun `세션이 없는 유저는 NO_ACTIVITY로 저장된다`() {
        val user = newUser()

        runner.run()

        val weekOf = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).minusWeeks(1)
        val analysis = analysisRepository.findByUserIdAndWeekOf(user.id!!, weekOf)
        assertEquals(AnalysisStatus.NO_ACTIVITY, analysis?.status)
    }

    @Test
    fun `세션이 있는 유저는 SUCCESS로 집계 저장되고, 존재하지 않는 exerciseId를 참조하는 유저는 FAILED로 저장되지만 배치는 계속된다`() {
        val goodUser = newUser()
        val exerciseId = savedStrengthExerciseId()
        val goodLog = SessionLog.create(ExerciseType.STRENGTH, exerciseId, sortOrder = 0, targetSets = 3, targetReps = 10, targetWeight = BigDecimal("60.0"))
            .copy(completed = true, actualSets = 3, actualReps = 10, actualWeight = BigDecimal("60.0"))
        sessionRepository.save(WorkoutSession(id = null, userId = goodUser.id!!, date = aDayLastWeek(), templateId = null, logs = listOf(goodLog)))

        val badUser = newUser()
        val nonExistentExerciseId = java.util.UUID.randomUUID()
        val badLog = SessionLog.create(ExerciseType.STRENGTH, nonExistentExerciseId, sortOrder = 0, targetSets = 3, targetReps = 10, targetWeight = BigDecimal("60.0"))
            .copy(completed = true, actualSets = 3, actualReps = 10, actualWeight = BigDecimal("60.0"))
        sessionRepository.save(WorkoutSession(id = null, userId = badUser.id!!, date = aDayLastWeek(), templateId = null, logs = listOf(badLog)))

        val exitCode = runner.run()

        val weekOf = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).minusWeeks(1)
        val goodAnalysis = analysisRepository.findByUserIdAndWeekOf(goodUser.id!!, weekOf)
        val badAnalysis = analysisRepository.findByUserIdAndWeekOf(badUser.id!!, weekOf)

        assertEquals(AnalysisStatus.SUCCESS, goodAnalysis?.status)
        assertEquals(BigDecimal("1800.0"), goodAnalysis?.summary?.volumeByExercise?.get(exerciseId))
        assertEquals(AnalysisStatus.FAILED, badAnalysis?.status)
        assertEquals(1, exitCode)
    }
}
