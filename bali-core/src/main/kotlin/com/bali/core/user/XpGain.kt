package com.bali.core.user

import com.bali.core.session.WorkoutSession
import com.bali.core.session.hasCountedLog

// 세션 완료로 얻은 XP 내역. before/after는 완료 전후의 레벨 스냅샷이고 earnedXp는 둘의 누적 XP 차이다
data class XpGain(
    val earnedXp: Int,
    val baseXp: Int,
    // 기본 XP를 넘는 부분(연속 보너스). 과거 날짜 세션을 완료해 뒤 날짜의 연속 보너스가 늘어난 몫도 여기 포함된다
    val bonusXp: Int,
    // earnedXp가 0일 때만 값이 있다
    val zeroReason: XpZeroReason?,
    // 부분 수행(정상 완료 조건은 못 채웠지만 기록된 수행량이 기준 이상) 세션이면 true
    val partial: Boolean,
    val before: UserLevel,
    val after: UserLevel,
) {
    companion object {
        // 완료 전후 레벨로 XP 내역을 만든다. tier는 완료한 세션의 인정 등급(정상/부분/없음)이다.
        // baseXp는 등급의 기본 XP이되 실제 얻은 XP를 넘지 않는다(한도 때문에 더 작은 세션을 밀어낸 경우 차이만 얻는다)
        fun between(before: UserLevel, after: UserLevel, tier: XpTier): XpGain {
            val earned = after.totalXp - before.totalXp
            val tierXp = when (tier) {
                XpTier.FULL -> UserLevel.BASE_SESSION_XP
                XpTier.PARTIAL -> UserLevel.PARTIAL_SESSION_XP
                XpTier.NONE -> 0
            }
            val base = if (earned > 0) minOf(tierXp, earned) else 0
            val zeroReason = when {
                earned > 0 -> null
                tier == XpTier.NONE -> XpZeroReason.NO_COMPLETED_LOG
                else -> XpZeroReason.DAILY_LIMIT
            }
            return XpGain(
                earnedXp = earned, baseXp = base, bonusXp = earned - base, zeroReason = zeroReason,
                partial = tier == XpTier.PARTIAL, before = before, after = after,
            )
        }
    }
}

// 경험치 인정 세션인지: 운동 통계와 같은 기준(WorkoutRecordPolicy)의 집계 대상 로그가 하나 이상 있어야 한다
fun WorkoutSession.isXpQualified(): Boolean = hasCountedLog()

// 완료 체크와 무관하게 기록된 수행량이 부분 수행 기준 이상인지: 근력은 reps>0인 로그의 actualSets 합, 유산소는 actualDurationSeconds 합
fun WorkoutSession.hasPartialRecord(): Boolean =
    logs.filter { (it.actualReps ?: 0) > 0 }.sumOf { it.actualSets ?: 0 } >= UserLevel.PARTIAL_MIN_SETS ||
        logs.sumOf { it.actualDurationSeconds ?: 0 } >= UserLevel.PARTIAL_MIN_CARDIO_SECONDS

// 세션의 XP 인정 등급: 정상 인정이면 FULL, 아니면 시작일 이후 세션 중 부분 수행 기준을 채운 경우 PARTIAL, 그 외 NONE
fun WorkoutSession.xpTier(): XpTier = when {
    isXpQualified() -> XpTier.FULL
    date >= UserLevel.PARTIAL_QUALIFICATION_FROM && hasPartialRecord() -> XpTier.PARTIAL
    else -> XpTier.NONE
}

// 세션의 XP 인정 등급
enum class XpTier { FULL, PARTIAL, NONE }

// 세션을 완료했는데 XP를 얻지 못한 이유
enum class XpZeroReason {
    // 하루 인정 세션 한도를 이미 채웠다
    DAILY_LIMIT,
    // 인정할 만한 완료 로그가 없다(완료 체크가 없거나, 기준일 이후 세션인데 세트·횟수 기록이 없다)
    NO_COMPLETED_LOG,
}
