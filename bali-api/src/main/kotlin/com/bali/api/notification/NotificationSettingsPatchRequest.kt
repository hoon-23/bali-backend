package com.bali.api.notification

// null인 필드는 변경하지 않음 (SessionPatchRequest와 동일한 부분 수정 관례)
data class NotificationSettingsPatchRequest(
    val routineReminderEnabled: Boolean? = null,
    val inactivityAlertEnabled: Boolean? = null,
    val summaryNotificationEnabled: Boolean? = null,
)
