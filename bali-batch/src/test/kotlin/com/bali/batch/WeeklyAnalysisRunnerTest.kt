package com.bali.batch

import com.bali.core.analysis.AnalysisStatus
import com.bali.core.analysis.AnalysisSummary
import com.bali.core.analysis.WeeklyAnalysis
import com.bali.core.analysis.WeeklyAnalysisRepository
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
        AnalysisPeriod.lastCompletedWeekOf(AnalysisPeriod.todayInApp()).plusDays(1)

    @Test
    fun `세션이 없는 유저는 NO_ACTIVITY로 저장된다`() {
        val user = newUser()

        runner.run()

        val weekOf = AnalysisPeriod.lastCompletedWeekOf(AnalysisPeriod.todayInApp())
        val analysis = analysisRepository.findByUserIdAndWeekOf(user.id!!, weekOf)
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
        sessionRepository.save(WorkoutSession(id = null, userId = goodUser.id!!, date = aDayLastWeek(), templateId = null, status = SessionStatus.SCHEDULED, logs = listOf(goodLog)))

        val exitCode = runner.run()

        val weekOf = AnalysisPeriod.lastCompletedWeekOf(AnalysisPeriod.todayInApp())
        val goodAnalysis = analysisRepository.findByUserIdAndWeekOf(goodUser.id!!, weekOf)

        assertEquals(AnalysisStatus.SUCCESS, goodAnalysis?.status)
        assertEquals(BigDecimal("1800.0"), goodAnalysis?.summary?.volumeByExercise?.get(exerciseId))
        assertEquals(0, exitCode)
    }

    // 직전 주 분석(요약 지정)을 저장하고 지난주에 1800 볼륨 세션 1개를 만든 뒤 배치를 돌려 결과를 반환
    private fun runWithPreviousWeek(previousSummary: AnalysisSummary): WeeklyAnalysis {
        val user = newUser()
        val exerciseId = savedStrengthExerciseId()
        val weekOf = AnalysisPeriod.lastCompletedWeekOf(AnalysisPeriod.todayInApp())
        analysisRepository.save(
            WeeklyAnalysis(
                id = null, userId = user.id!!, weekOf = weekOf.minusWeeks(1), status = AnalysisStatus.SUCCESS,
                summary = previousSummary.copy(volumeByExercise = mapOf(exerciseId to BigDecimal("3000.0"))), insights = emptyList(),
            )
        )
        val log = SessionLog.create(ExerciseType.STRENGTH, exerciseId, sortOrder = 0, targetSets = 3, targetReps = 10, targetWeight = BigDecimal("60.0"))
            .copy(completed = true, actualSets = 3, actualReps = 10, actualWeight = BigDecimal("60.0"))
        sessionRepository.save(WorkoutSession(id = null, userId = user.id!!, date = aDayLastWeek(), templateId = null, status = SessionStatus.COMPLETED, logs = listOf(log)))

        runner.run()

        return analysisRepository.findByUserIdAndWeekOf(user.id!!, weekOf)!!
    }

    // 직전 주 요약에 세션 수가 있으면 빈도는 횟수로, 강도는 세션당 평균(1800 vs 3000/2회=1500)으로 비교한다
    @Test
    fun `직전 주 요약에 세션 수가 있으면 횟수 비교와 세션당 볼륨 증감 인사이트가 저장된다`() {
        val previous = AnalysisSummary(0, emptyMap(), emptyMap(), 0, BigDecimal("100.0"), null, sessionCount = 2, weightedSessionCount = 2)

        val analysis = runWithPreviousWeek(previous)

        assertEquals(1, analysis.summary?.sessionCount)
        assertEquals(2, analysis.summary?.previousSessionCount)
        assertEquals(BigDecimal("20.0"), analysis.summary?.volumeChangeFromLastWeekPercent)
        assertEquals(
            listOf("지난주 2회 → 이번 주 1회", "이번 주 세션당 볼륨이 지난주보다 20.0% 증가했어요"),
            analysis.insights.map { it.summaryText },
        )
    }

    // 세션 수가 없는 구버전 요약이 직전 주면 증감/빈도 문장을 만들지 않는다 (백필 안 함)
    @Test
    fun `직전 주 요약이 세션 수 없는 구버전이면 증감과 빈도 인사이트를 만들지 않는다`() {
        val legacy = AnalysisSummary(0, emptyMap(), emptyMap(), 0, BigDecimal("100.0"), null)

        val analysis = runWithPreviousWeek(legacy)

        assertEquals(1, analysis.summary?.sessionCount)
        assertEquals(null, analysis.summary?.previousSessionCount)
        assertEquals(null, analysis.summary?.volumeChangeFromLastWeekPercent)
        assertEquals(emptyList<String>(), analysis.insights.map { it.summaryText })
    }
}
