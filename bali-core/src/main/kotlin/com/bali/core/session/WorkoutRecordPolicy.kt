package com.bali.core.session

import java.time.LocalDate

// "실제로 운동한 기록"으로 셀 로그/세션의 기준. XP와 운동 통계(일별/주간/월간/운동일)가 같은 기준을 쓴다
object WorkoutRecordPolicy {
    // 이 날짜(세션 date, 한국 기준) 이후 세션부터 세트·횟수(유산소는 시간)를 실제로 기록한 완료 로그만 센다.
    // 이전 세션은 소급하지 않고 완료 체크만으로 센다(옛 기록은 세트·횟수가 비어 있는 경우가 많다)
    val STRICT_FROM: LocalDate = LocalDate.of(2026, 10, 2)
}

// 로그가 실제 수행 기록을 가졌는지: 근력은 세트와 횟수가 모두 0보다 크고, 유산소는 수행 시간이 0보다 크다
fun SessionLog.hasPerformanceRecord(): Boolean =
    ((actualSets ?: 0) > 0 && (actualReps ?: 0) > 0) || (actualDurationSeconds ?: 0) > 0

// 집계 대상 로그인지: 완료 체크가 있어야 하고, 기준일 이후 세션의 로그는 수행 기록도 있어야 한다
fun SessionLog.countsAsWorkout(sessionDate: LocalDate): Boolean =
    completed && (sessionDate < WorkoutRecordPolicy.STRICT_FROM || hasPerformanceRecord())

// 세션의 집계 대상 로그 목록
fun WorkoutSession.countedLogs(): List<SessionLog> = logs.filter { it.countsAsWorkout(date) }

// 집계 대상 로그가 하나라도 있는 세션인지 (운동한 세션으로 셀지)
fun WorkoutSession.hasCountedLog(): Boolean = logs.any { it.countsAsWorkout(date) }

// 완료 세션 수에 셀 세션인지: COMPLETED여야 하고, 기준일 이후 세션은 집계 대상 로그도 있어야 한다 (기준일 이전은 상태만 본다)
fun WorkoutSession.countsAsCompletedSession(): Boolean =
    status == SessionStatus.COMPLETED && (date < WorkoutRecordPolicy.STRICT_FROM || hasCountedLog())
