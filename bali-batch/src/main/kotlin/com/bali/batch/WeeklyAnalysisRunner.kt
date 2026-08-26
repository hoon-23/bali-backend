package com.bali.batch

import com.bali.core.analysis.AnalysisStatus
import com.bali.core.analysis.WeeklyAnalysis
import com.bali.core.analysis.WeeklyAnalysisRepository
import com.bali.core.analysis.WeeklyStatsCalculator
import com.bali.core.exercise.ExerciseRepository
import com.bali.core.exercise.findAllByIds
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
        return runResiliently(
            items = userRepository.findAllByStatus(UserStatus.ACTIVE),
            process = { user -> processUser(user, weekOf) },
            onFailure = { user, e ->
                log.error("주간 분석 실패: userId=${user.id}, weekOf=$weekOf", e)
                // 다른 알림 러너들과 달리 실패도 명시적으로 FAILED 레코드를 남긴다 —
                // 레코드가 아예 없으면 "아직 배치가 안 돌았음"과 "돌았는데 실패함"을 구분할 수 없기 때문
                analysisRepository.save(
                    WeeklyAnalysis(id = null, userId = user.id!!, weekOf = weekOf, status = AnalysisStatus.FAILED, summary = null, insights = emptyList())
                )
            },
        )
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
        val exercisesById = exerciseRepository.findAllByIds(logs.map { it.exerciseId })

        val previousSummary = analysisRepository.findByUserIdAndWeekOf(userId, weekOf.minusWeeks(1))?.summary
        val summary = WeeklyStatsCalculator.calculate(logs, exercisesById, previousSummary)
        val insights = WeeklyStatsCalculator.generateInsights(summary)

        analysisRepository.save(
            WeeklyAnalysis(id = null, userId = userId, weekOf = weekOf, status = AnalysisStatus.SUCCESS, summary = summary, insights = insights)
        )
    }
}
