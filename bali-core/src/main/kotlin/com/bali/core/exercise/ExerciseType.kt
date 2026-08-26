package com.bali.core.exercise

enum class ExerciseType {
    STRENGTH, CARDIO;

    // 이 타입에 속하지 않는 필드 그룹(반대 타입 전용 필드)이 채워져 있으면 예외.
    // TemplateItem/SessionLog가 각자 STRENGTH-CARDIO 필드 조합 규칙을 따로 구현하다 어긋난 적이 있어 이 규칙만 공유 지점으로 뺐다.
    // 그룹 자체의 필수 여부(예: TemplateItem은 자기 타입 필드가 전부 있어야 함, SessionLog는 비어 있어도 됨)는 호출부 책임으로 남긴다.
    fun requireNoForeignGroupFields(
        hasStrengthField: Boolean,
        hasCardioField: Boolean,
        strengthFieldNames: String,
        cardioFieldNames: String,
    ) {
        when (this) {
            STRENGTH -> require(!hasCardioField) { "STRENGTH exercise must not have $cardioFieldNames" }
            CARDIO -> require(!hasStrengthField) { "CARDIO exercise must not have $strengthFieldNames" }
        }
    }
}
