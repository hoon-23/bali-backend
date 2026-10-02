package com.bali.core.notification

// 알림 발송 포트. 실전 구현은 Expo Push API 호출(ExpoPushSender), 테스트는 fake로 대체
fun interface NotificationSender {
    fun send(messages: List<PushMessage>): List<PushSendResult>
}

// badge는 앱 아이콘에 표시할 절대값(iOS 전용). null이면 아이콘 뱃지를 건드리지 않는다
data class PushMessage(val token: String, val title: String, val body: String, val data: Map<String, String> = emptyMap(), val badge: Int? = null)

data class PushSendResult(val token: String, val ticketId: String?, val error: PushSendError?)

enum class PushSendError { DEVICE_NOT_REGISTERED, OTHER }
