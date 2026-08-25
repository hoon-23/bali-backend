package com.bali.infra.notification

import com.bali.core.notification.NotificationSender
import com.bali.core.notification.PushMessage
import com.bali.core.notification.PushSendError
import com.bali.core.notification.PushSendResult
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.springframework.web.client.RestClient

@JsonIgnoreProperties(ignoreUnknown = true)
data class ExpoTicketDetails(val error: String? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ExpoTicket(val status: String = "error", val id: String? = null, val message: String? = null, val details: ExpoTicketDetails? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ExpoPushSendResponse(val data: List<ExpoTicket> = emptyList())

data class ExpoPushMessageRequest(val to: String, val title: String, val body: String, val data: Map<String, String> = emptyMap())

// Expo Push API로 알림을 발송하는 NotificationSender 구현. 최대 100개씩 배치 요청한다(Expo 제약)
class ExpoPushSender(private val restClient: RestClient) : NotificationSender {

    companion object {
        private const val BATCH_SIZE = 100
        private const val EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send"
    }

    override fun send(messages: List<PushMessage>): List<PushSendResult> =
        messages.chunked(BATCH_SIZE).flatMap { sendBatch(it) }

    // Expo는 요청 배열과 동일한 순서로 티켓 배열을 반환한다고 문서화되어 있어 zip으로 매칭한다
    private fun sendBatch(batch: List<PushMessage>): List<PushSendResult> {
        val response = restClient.post()
            .uri(EXPO_PUSH_URL)
            .body(batch.map { ExpoPushMessageRequest(to = it.token, title = it.title, body = it.body, data = it.data) })
            .retrieve()
            .body(ExpoPushSendResponse::class.java) ?: ExpoPushSendResponse()

        return batch.zip(response.data).map { (message, ticket) ->
            if (ticket.status == "ok") {
                PushSendResult(token = message.token, ticketId = ticket.id, error = null)
            } else {
                val error = if (ticket.details?.error == "DeviceNotRegistered") PushSendError.DEVICE_NOT_REGISTERED else PushSendError.OTHER
                PushSendResult(token = message.token, ticketId = null, error = error)
            }
        }
    }
}
