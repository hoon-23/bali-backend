package com.bali.core.notification

// 알림 발송 포트. 실전 구현은 Expo Push API 호출(ExpoPushSender), 테스트는 fake로 대체
fun interface NotificationSender {
    fun send(messages: List<PushMessage>): List<PushSendResult>
}

data class PushMessage(val token: String, val title: String, val body: String, val data: Map<String, String> = emptyMap())

data class PushSendResult(val token: String, val ticketId: String?, val error: PushSendError?)

enum class PushSendError { DEVICE_NOT_REGISTERED, OTHER }
