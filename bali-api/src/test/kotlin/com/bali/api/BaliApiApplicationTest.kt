package com.bali.api

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.junit.jupiter.api.Assertions.assertEquals

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BaliApiApplicationTest {

    @LocalServerPort
    var port: Int = 0

    private val restTemplate = TestRestTemplate()

    @Test
    fun `actuator health endpoint responds UP`() {
        val response = restTemplate.getForEntity(
            "http://localhost:$port/actuator/health",
            String::class.java,
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assert(response.body?.contains("UP") == true)
    }
}
