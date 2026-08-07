package com.bali.batch

import com.bali.core.analysis.AnalysisStatus
import com.bali.core.analysis.WeeklyAnalysis
import com.bali.core.analysis.WeeklyAnalysisRepository
import com.bali.core.analysis.WeeklyStatsCalculator
import com.bali.core.exercise.ExerciseRepository
import com.bali.core.session.WorkoutSessionRepository
import com.bali.core.user.User
import com.bali.core.user.UserRepository
import com.bali.core.user.UserStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

// 매주 월요일 실행되어 ACTIVE 유저별로 직전 완료 주(월~일)의 운동 기록을 집계하고 WeeklyAnalysis/Insight를 저장
@Component
class WeeklyAnalysisRunner(
    private val userRepository: UserRepository,
    private val sessionRepository: WorkoutSessionRepository,
    private val exerciseRepository: ExerciseRepository,
    private val analysisRepository: WeeklyAnalysisRepository,
) {
    private val log = LoggerFactory.getLogger(WeeklyAnalysisRunner::class.java)

    // 실행 진입점. 전원 성공하면 0, 하나 이상 실패했으면 1을 반환 (Airflow가 재시도 여부를 판단하는 데 사용)
    fun run(): Int {
        val weekOf = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
        var hadFailure = false

        userRepository.findAllByStatus(UserStatus.ACTIVE).forEach { user ->
            try {
                processUser(user, weekOf)
            } catch (e: Exception) {
                log.error("주간 분석 실패: userId=${user.id}, weekOf=$weekOf", e)
                analysisRepository.save(
                    WeeklyAnalysis(id = null, userId = user.id!!, weekOf = weekOf, status = AnalysisStatus.FAILED, summary = null, insights = emptyList())
                )
                hadFailure = true
            }
        }
        return if (hadFailure) 1 else 0
    }

    // 유저 1명의 직전 완료 주 기록을 집계해 WeeklyAnalysis를 저장
    private fun processUser(user: User, weekOf: LocalDate) {
        val userId = user.id!!
        val sessions = sessionRepository.findAllByUserId(userId, weekOf, weekOf.plusDays(6))
        if (sessions.isEmpty()) {
            analysisRepository.save(
                WeeklyAnalysis(id = null, userId = userId, weekOf = weekOf, status = AnalysisStatus.NO_ACTIVITY, summary = null, insights = emptyList())
            )
            return
        }

        val logs = sessions.flatMap { it.logs }
        val exercisesById = logs.map { it.exerciseId }.distinct().associateWith { exerciseId ->
            exerciseRepository.findById(exerciseId) ?: throw IllegalStateException("존재하지 않는 exerciseId: $exerciseId")
        }

        val previousSummary = analysisRepository.findByUserIdAndWeekOf(userId, weekOf.minusWeeks(1))?.summary
        val summary = WeeklyStatsCalculator.calculate(logs, exercisesById, previousSummary)
        val insights = WeeklyStatsCalculator.generateInsights(summary)

        analysisRepository.save(
            WeeklyAnalysis(id = null, userId = userId, weekOf = weekOf, status = AnalysisStatus.SUCCESS, summary = summary, insights = insights)
        )
    }
}
