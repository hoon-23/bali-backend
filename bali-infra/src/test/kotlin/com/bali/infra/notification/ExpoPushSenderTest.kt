package com.bali.infra.notification

import com.bali.core.notification.PushMessage
import com.bali.core.notification.PushSendError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class ExpoPushSenderTest {

    @Test
    fun `정상 발송 시 티켓ID를 반환한다`() {
        val builder = RestClient.builder()
        val mockServer = MockRestServiceServer.bindTo(builder).build()
        val restClient = builder.build()

        mockServer.expect(requestTo("https://exp.host/--/api/v2/push/send"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""{"data":[{"status":"ok","id":"ticket-1"}]}""", MediaType.APPLICATION_JSON))

        val sender = ExpoPushSender(restClient)
        val results = sender.send(listOf(PushMessage(token = "ExponentPushToken[abc]", title = "제목", body = "본문")))

        assertEquals(1, results.size)
        assertEquals("ticket-1", results[0].ticketId)
        assertNull(results[0].error)
        mockServer.verify()
    }

    @Test
    fun `DeviceNotRegistered 에러는 DEVICE_NOT_REGISTERED로 매핑된다`() {
        val builder = RestClient.builder()
        val mockServer = MockRestServiceServer.bindTo(builder).build()
        val restClient = builder.build()

        mockServer.expect(requestTo("https://exp.host/--/api/v2/push/send"))
            .andRespond(withSuccess("""{"data":[{"status":"error","message":"not registered","details":{"error":"DeviceNotRegistered"}}]}""", MediaType.APPLICATION_JSON))

        val sender = ExpoPushSender(restClient)
        val results = sender.send(listOf(PushMessage(token = "ExponentPushToken[dead]", title = "제목", body = "본문")))

        assertEquals(PushSendError.DEVICE_NOT_REGISTERED, results[0].error)
        assertNull(results[0].ticketId)
        mockServer.verify()
    }

    @Test
    fun `101개 메시지는 100개씩 두 번의 배치 요청으로 나뉜다`() {
        val builder = RestClient.builder()
        val mockServer = MockRestServiceServer.bindTo(builder).build()
        val restClient = builder.build()

        val firstBatchTickets = (1..100).joinToString(",") { """{"status":"ok","id":"ticket-$it"}""" }
        mockServer.expect(requestTo("https://exp.host/--/api/v2/push/send"))
            .andRespond(withSuccess("""{"data":[$firstBatchTickets]}""", MediaType.APPLICATION_JSON))
        mockServer.expect(requestTo("https://exp.host/--/api/v2/push/send"))
            .andRespond(withSuccess("""{"data":[{"status":"ok","id":"ticket-101"}]}""", MediaType.APPLICATION_JSON))

        val sender = ExpoPushSender(restClient)
        val messages = (1..101).map { PushMessage(token = "ExponentPushToken[$it]", title = "제목", body = "본문") }
        val results = sender.send(messages)

        assertEquals(101, results.size)
        assertEquals("ticket-101", results[100].ticketId)
        mockServer.verify()
    }
}
