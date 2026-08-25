package com.bali.api.notification

import com.bali.core.notification.NotificationSettings

data class NotificationSettingsResponse(
    val routineReminderEnabled: Boolean,
    val inactivityAlertEnabled: Boolean,
    val summaryNotificationEnabled: Boolean,
) {
    companion object {
        fun from(settings: NotificationSettings) = NotificationSettingsResponse(
            routineReminderEnabled = settings.routineReminderEnabled,
            inactivityAlertEnabled = settings.inactivityAlertEnabled,
            summaryNotificationEnabled = settings.summaryNotificationEnabled,
        )
    }
}
