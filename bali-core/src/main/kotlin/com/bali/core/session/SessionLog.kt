package com.bali.core.session

import com.bali.core.exercise.ExerciseType
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

// 세트 하나의 시작/종료 시각. 휴식시간/총 운동시간 계산은 클라이언트가 이 원본값으로 직접 수행한다
data class SetTiming(val setIndex: Int, val startedAt: Instant, val endedAt: Instant)

// 세션에 속한 운동 수행 기록 (계획된 target값 + 실제 수행한 actual값)
data class SessionLog(
    val id: UUID?,
    val exerciseId: UUID,
    val sortOrder: Int,
    val completed: Boolean = false,
    val targetSets: Int?,
    val targetReps: Int?,
    val targetWeight: BigDecimal?,
    val targetDurationSeconds: Int?,
    val targetPace: String?,
    val actualSets: Int?,
    val actualReps: Int?,
    val actualWeight: BigDecimal?,
    val actualDurationSeconds: Int?,
    val actualPace: String?,
    val setTimings: List<SetTiming>? = null,
) {
    companion object {
        // 템플릿 스냅샷 복사 또는 즉흥 추가(target 전부 null 허용)로 SessionLog 생성
        fun create(
            exerciseType: ExerciseType,
            exerciseId: UUID,
            sortOrder: Int,
            targetSets: Int? = null,
            targetReps: Int? = null,
            targetWeight: BigDecimal? = null,
            targetDurationSeconds: Int? = null,
            targetPace: String? = null,
        ): SessionLog {
            val strengthFieldsSet = targetSets != null || targetReps != null || targetWeight != null
            val cardioFieldsSet = targetDurationSeconds != null || targetPace != null
            require(!(strengthFieldsSet && cardioFieldsSet)) { "target 필드는 STRENGTH/CARDIO 그룹 중 하나만 채워야 함" }
            when (exerciseType) {
                ExerciseType.STRENGTH -> require(targetDurationSeconds == null && targetPace == null) {
                    "STRENGTH exercise must not have targetDurationSeconds/targetPace"
                }
                ExerciseType.CARDIO -> require(targetSets == null && targetReps == null && targetWeight == null) {
                    "CARDIO exercise must not have targetSets/targetReps/targetWeight"
                }
            }
            return SessionLog(
                id = null, exerciseId = exerciseId, sortOrder = sortOrder, completed = false,
                targetSets = targetSets, targetReps = targetReps, targetWeight = targetWeight,
                targetDurationSeconds = targetDurationSeconds, targetPace = targetPace,
                actualSets = null, actualReps = null, actualWeight = null,
                actualDurationSeconds = null, actualPace = null,
            )
        }

        // PATCH .../logs/{logId}로 들어온 actual값이 exerciseType에 맞는지 검증
        fun validateActualFields(
            exerciseType: ExerciseType,
            actualSets: Int?, actualReps: Int?, actualWeight: BigDecimal?,
            actualDurationSeconds: Int?, actualPace: String?,
        ) {
            val strengthFieldsSet = actualSets != null || actualReps != null || actualWeight != null
            val cardioFieldsSet = actualDurationSeconds != null || actualPace != null
            require(!(strengthFieldsSet && cardioFieldsSet)) { "actual 필드는 STRENGTH/CARDIO 그룹 중 하나만 채워야 함" }
            when (exerciseType) {
                ExerciseType.STRENGTH -> require(actualDurationSeconds == null && actualPace == null) {
                    "STRENGTH exercise must not have actualDurationSeconds/actualPace"
                }
                ExerciseType.CARDIO -> require(actualSets == null && actualReps == null && actualWeight == null) {
                    "CARDIO exercise must not have actualSets/actualReps/actualWeight"
                }
            }
        }

        // setTimings가 exerciseType/타임스탬프 제약을 만족하는지 검증 (CARDIO는 세트 개념이 없어 setTimings 불허)
        fun validateSetTimings(exerciseType: ExerciseType, setTimings: List<SetTiming>?) {
            if (exerciseType == ExerciseType.CARDIO) {
                require(setTimings == null) { "CARDIO exercise must not have setTimings" }
            }
            setTimings?.forEach { st ->
                require(st.endedAt > st.startedAt) { "setTiming.endedAt must be after startedAt (setIndex=${st.setIndex})" }
            }
        }
    }
}
