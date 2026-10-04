package com.bali.api.plan

import com.bali.api.auth.jwt.JwtTokenProvider
import com.bali.core.plan.Plan
import com.bali.core.user.AuthProvider
import com.bali.infra.user.UserJpaEntity
import com.bali.infra.user.UserJpaRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

// 한도를 켠(limits-enabled=true) 설정에서 무료 플랜 한도가 API에 적용되는지 검증
@SpringBootTest(properties = ["bali.plan.limits-enabled=true"])
@AutoConfigureMockMvc
@Transactional
class PlanLimitsApiTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired lateinit var userJpaRepository: UserJpaRepository

    // 지정 플랜의 테스트 사용자를 만들고 (JWT, userId) 반환
    private fun newUser(plan: Plan = Plan.FREE, expires: Instant? = null): Pair<String, UUID> {
        val n = System.nanoTime()
        val entity = userJpaRepository.save(
            UserJpaEntity(email = "plan-$n@example.com", provider = AuthProvider.GOOGLE, providerId = "sub-plan-$n", plan = plan, planExpiresAt = expires)
        )
        return jwtTokenProvider.generateToken(entity.id, entity.email) to entity.id
    }

    // 개인 운동 하나를 POST로 만든다 (expect: 기대 상태)
    private fun createExercise(token: String, i: Int) =
        mockMvc.perform(
            post("/api/v1/exercises").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"한도테스트운동$i","muscleGroup":"CHEST","type":"STRENGTH"}""")
        )

    // 빈 items 루틴 하나를 POST로 만든다
    private fun createTemplate(token: String, i: Int) =
        mockMvc.perform(
            post("/api/v1/templates").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON)
                .content("""{"category":"PUSH","name":"한도루틴$i","items":[]}""")
        )

    @Test
    fun `FREE는 루틴 5개까지 만들고 6번째는 403 LIMIT_EXCEEDED이며 삭제하면 다시 만들 수 있다`() {
        val (token, _) = newUser()
        val ids = (1..5).map {
            val body = createTemplate(token, it).andExpect(status().isCreated).andReturn().response.contentAsString
            Regex("\"id\":\"([^\"]+)\"").find(body)!!.groupValues[1]
        }

        createTemplate(token, 6).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED"))
            .andExpect(jsonPath("$.limit").value("TEMPLATE_COUNT"))

        mockMvc.perform(delete("/api/v1/templates/${ids.first()}").header("Authorization", "Bearer $token")).andExpect(status().isNoContent)
        createTemplate(token, 7).andExpect(status().isCreated)
    }

    @Test
    fun `FREE는 직접 만든 운동 10개까지 만들고 11번째는 403이다`() {
        val (token, _) = newUser()
        (1..10).forEach { createExercise(token, it).andExpect(status().isCreated) }

        createExercise(token, 11).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED"))
            .andExpect(jsonPath("$.limit").value("PERSONAL_EXERCISE_COUNT"))
    }

    @Test
    fun `삭제한 개인 운동은 한도 개수에서 빠져 다시 만들 수 있다`() {
        val (token, _) = newUser()
        val ids = (1..10).map {
            val body = createExercise(token, it).andExpect(status().isCreated).andReturn().response.contentAsString
            Regex("\"id\":\"([^\"]+)\"").find(body)!!.groupValues[1]
        }
        createExercise(token, 11).andExpect(status().isForbidden)

        mockMvc.perform(delete("/api/v1/exercises/${ids.first()}").header("Authorization", "Bearer $token")).andExpect(status().isNoContent)

        createExercise(token, 12).andExpect(status().isCreated)
    }

    @Test
    fun `PRO는 한도를 넘겨도 만들 수 있고 만료된 PRO는 FREE로 취급된다`() {
        val (proToken, _) = newUser(Plan.PRO)
        (1..6).forEach { createTemplate(proToken, it).andExpect(status().isCreated) }

        val (expiredToken, _) = newUser(Plan.PRO, expires = Instant.now().minusSeconds(60))
        (1..5).forEach { createTemplate(expiredToken, it).andExpect(status().isCreated) }
        createTemplate(expiredToken, 6).andExpect(status().isForbidden)
    }

    @Test
    fun `월간 분석 3개 엔드포인트는 FREE에서 403이고 PRO에서 허용되며 주간은 FREE도 허용된다`() {
        val (freeToken, _) = newUser()
        listOf("/api/v1/analysis/monthly", "/api/v1/analysis/monthly/current", "/api/v1/analysis/monthly/2026-09-01").forEach {
            mockMvc.perform(get(it).header("Authorization", "Bearer $freeToken")).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.limit").value("MONTHLY_INSIGHTS"))
        }
        mockMvc.perform(get("/api/v1/analysis/weekly").header("Authorization", "Bearer $freeToken")).andExpect(status().isOk)

        val (proToken, _) = newUser(Plan.PRO)
        mockMvc.perform(get("/api/v1/analysis/monthly").header("Authorization", "Bearer $proToken")).andExpect(status().isOk)
    }

    @Test
    fun `내 정보 조회는 유효 플랜을 내려준다`() {
        val (token, _) = newUser(Plan.PRO, expires = Instant.now().minusSeconds(60))
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk).andExpect(jsonPath("$.plan").value("FREE"))
    }
}
