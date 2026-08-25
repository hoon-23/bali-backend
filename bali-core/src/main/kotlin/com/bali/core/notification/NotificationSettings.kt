package com.bali.core.notification

import java.util.UUID

// 유저별 알림 유형 on/off 설정. 행이 없는 유저는 defaults()로 전부 켜진 상태로 취급한다
data class NotificationSettings(
    val userId: UUID,
    val routineReminderEnabled: Boolean = true,
    val inactivityAlertEnabled: Boolean = true,
    val summaryNotificationEnabled: Boolean = true,
) {
    companion object {
        fun defaults(userId: UUID) = NotificationSettings(userId)
    }
}
