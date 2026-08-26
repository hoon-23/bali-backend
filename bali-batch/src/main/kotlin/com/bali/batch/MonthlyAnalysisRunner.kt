package com.bali.batch

import com.bali.core.analysis.AnalysisStatus
import com.bali.core.analysis.MonthlyAnalysis
import com.bali.core.analysis.MonthlyAnalysisRepository
import com.bali.core.analysis.MonthlyStatsCalculator
import com.bali.core.exercise.ExerciseRepository
import com.bali.core.exercise.findAllByIds
import com.bali.core.session.WorkoutSessionRepository
import com.bali.core.user.User
import com.bali.core.user.UserRepository
import com.bali.core.user.UserStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate

// 매달 1일 실행되어 ACTIVE 유저별로 직전 완료 달의 운동 기록을 집계하고 MonthlyAnalysis/Insight를 저장
@Component
class MonthlyAnalysisRunner(
    private val userRepository: UserRepository,
    private val sessionRepository: WorkoutSessionRepository,
    private val exerciseRepository: ExerciseRepository,
    private val analysisRepository: MonthlyAnalysisRepository,
) {
    private val log = LoggerFactory.getLogger(MonthlyAnalysisRunner::class.java)

    // 실행 진입점. 전원 성공하면 0, 하나 이상 실패했으면 1을 반환 (Airflow가 재시도 여부를 판단하는 데 사용)
    fun run(): Int {
        val monthOf = LocalDate.now().withDayOfMonth(1).minusMonths(1)
        return runResiliently(
            items = userRepository.findAllByStatus(UserStatus.ACTIVE),
            process = { user -> processUser(user, monthOf) },
            onFailure = { user, e ->
                log.error("월간 분석 실패: userId=${user.id}, monthOf=$monthOf", e)
                // WeeklyAnalysisRunner와 동일하게 실패도 명시적으로 FAILED 레코드를 남긴다 —
                // 레코드가 아예 없으면 "아직 배치가 안 돌았음"과 "돌았는데 실패함"을 구분할 수 없기 때문
                analysisRepository.save(
                    MonthlyAnalysis(id = null, userId = user.id!!, monthOf = monthOf, status = AnalysisStatus.FAILED, summary = null, insights = emptyList())
                )
            },
        )
    }

    // 유저 1명의 직전 완료 달 기록을 집계해 MonthlyAnalysis를 저장
    private fun processUser(user: User, monthOf: LocalDate) {
        val userId = user.id!!
        val sessions = sessionRepository.findAllByUserId(userId, monthOf, monthOf.plusMonths(1).minusDays(1))
        if (sessions.isEmpty()) {
            analysisRepository.save(
                MonthlyAnalysis(id = null, userId = userId, monthOf = monthOf, status = AnalysisStatus.NO_ACTIVITY, summary = null, insights = emptyList())
            )
            return
        }

        val logs = sessions.flatMap { it.logs }
        val exercisesById = exerciseRepository.findAllByIds(logs.map { it.exerciseId })

        val previousSummary = analysisRepository.findByUserIdAndMonthOf(userId, monthOf.minusMonths(1))?.summary
        val summary = MonthlyStatsCalculator.calculate(logs, exercisesById, previousSummary)
        val insights = MonthlyStatsCalculator.generateInsights(summary)

        analysisRepository.save(
            MonthlyAnalysis(id = null, userId = userId, monthOf = monthOf, status = AnalysisStatus.SUCCESS, summary = summary, insights = insights)
        )
    }
}
