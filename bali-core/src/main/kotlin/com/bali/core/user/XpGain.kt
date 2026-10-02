package com.bali.core.user

import com.bali.core.session.SessionLog
import com.bali.core.session.WorkoutSession

// 세션 완료로 얻은 XP 내역. before/after는 완료 전후의 레벨 스냅샷이고 earnedXp는 둘의 누적 XP 차이다
data class XpGain(
    val earnedXp: Int,
    val baseXp: Int,
    // 기본 XP를 넘는 부분(연속 보너스). 과거 날짜 세션을 완료해 뒤 날짜의 연속 보너스가 늘어난 몫도 여기 포함된다
    val bonusXp: Int,
    // earnedXp가 0일 때만 값이 있다
    val zeroReason: XpZeroReason?,
    val before: UserLevel,
    val after: UserLevel,
) {
    companion object {
        // 완료 전후 레벨로 XP 내역을 만든다. hasCompletedLog는 완료한 세션이 인정 조건(isXpQualified)을 만족하는지
        fun between(before: UserLevel, after: UserLevel, hasCompletedLog: Boolean): XpGain {
            val earned = after.totalXp - before.totalXp
            val base = if (earned > 0) UserLevel.BASE_SESSION_XP else 0
            val zeroReason = when {
                earned > 0 -> null
                !hasCompletedLog -> XpZeroReason.NO_COMPLETED_LOG
                else -> XpZeroReason.DAILY_LIMIT
            }
            return XpGain(earnedXp = earned, baseXp = base, bonusXp = earned - base, zeroReason = zeroReason, before = before, after = after)
        }
    }
}

// 로그가 실제 수행 기록을 가졌는지: 근력은 세트와 횟수가 모두 0보다 크고, 유산소는 수행 시간이 0보다 크다
fun SessionLog.hasPerformanceRecord(): Boolean =
    ((actualSets ?: 0) > 0 && (actualReps ?: 0) > 0) || (actualDurationSeconds ?: 0) > 0

// 경험치 인정 세션인지: 완료 로그가 있어야 하고, 기준일(STRICT_QUALIFICATION_FROM) 이후 세션은 그 로그에 수행 기록도 있어야 한다
fun WorkoutSession.isXpQualified(): Boolean =
    logs.any { it.completed && (date < UserLevel.STRICT_QUALIFICATION_FROM || it.hasPerformanceRecord()) }

// 세션을 완료했는데 XP를 얻지 못한 이유
enum class XpZeroReason {
    // 하루 인정 세션 한도를 이미 채웠다
    DAILY_LIMIT,
    // 인정할 만한 완료 로그가 없다(완료 체크가 없거나, 기준일 이후 세션인데 세트·횟수 기록이 없다)
    NO_COMPLETED_LOG,
}
