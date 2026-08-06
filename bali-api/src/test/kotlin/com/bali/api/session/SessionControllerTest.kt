package com.bali.api.session

import com.bali.api.auth.jwt.JwtTokenProvider
import com.bali.core.exercise.Exercise
import com.bali.core.exercise.ExerciseRepository
import com.bali.core.exercise.ExerciseScope
import com.bali.core.exercise.ExerciseType
import com.bali.core.exercise.MuscleGroup
import com.bali.core.template.TemplateCategory
import com.bali.core.template.TemplateItem
import com.bali.core.template.WorkoutTemplate
import com.bali.core.template.WorkoutTemplateRepository
import com.bali.core.user.AuthProvider
import com.bali.infra.user.UserJpaEntity
import com.bali.infra.user.UserJpaRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.everyItem
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SessionControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired lateinit var userJpaRepository: UserJpaRepository
    @Autowired lateinit var exerciseRepository: ExerciseRepository
    @Autowired lateinit var templateRepository: WorkoutTemplateRepository
    @Autowired lateinit var objectMapper: ObjectMapper

    // 테스트용 사용자를 만들고 (JWT, userId) 쌍을 반환
    private fun issueTokenForNewUser(): Pair<String, UUID> {
        val entity = userJpaRepository.save(
            UserJpaEntity(email = "session-test@example.com", provider = AuthProvider.GOOGLE, providerId = "sub-session-${System.nanoTime()}")
        )
        return jwtTokenProvider.generateToken(entity.id, entity.email) to entity.id
    }

    // 테스트용 STRENGTH 종목을 저장하고 id 반환
    private fun savedStrengthExerciseId(): UUID =
        exerciseRepository.save(
            Exercise(id = null, name = "세션테스트벤치프레스", variant = null, muscleGroup = MuscleGroup.CHEST, type = ExerciseType.STRENGTH, scope = ExerciseScope.GLOBAL, ownerId = null)
        ).id!!

    @Test
    fun `POST sessions templateId로 호출하면 템플릿 items를 target 스냅샷으로 복사한다`() {
        val (token, userId) = issueTokenForNewUser()
        val exerciseId = savedStrengthExerciseId()
        val template = templateRepository.save(
            WorkoutTemplate(
                id = null, userId = userId, category = TemplateCategory.PUSH, name = "템플릿",
                items = listOf(TemplateItem.create(ExerciseType.STRENGTH, exerciseId, 0, targetSets = 3, targetReps = 10, targetWeight = BigDecimal("60.0"))),
            )
        )
        val body = """{"date":"2026-08-06","templateId":"${template.id}"}"""

        mockMvc.perform(post("/api/v1/sessions").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.logs[0].targetSets").value(3))
            .andExpect(jsonPath("$.logs[0].completed").value(false))
    }

    @Test
    fun `POST sessions templateId 없이 호출하면 빈 세션이 생성된다`() {
        val (token, _) = issueTokenForNewUser()
        val body = """{"date":"2026-08-06","templateId":null}"""

        mockMvc.perform(post("/api/v1/sessions").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.logs.length()").value(0))
    }

    @Test
    fun `GET sessions from to 호출하면 기간 내 세션만 반환한다`() {
        val (token, _) = issueTokenForNewUser()
        mockMvc.perform(post("/api/v1/sessions").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content("""{"date":"2026-08-06","templateId":null}"""))
            .andExpect(status().isCreated)
        mockMvc.perform(post("/api/v1/sessions").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content("""{"date":"2026-01-01","templateId":null}"""))
            .andExpect(status().isCreated)

        mockMvc.perform(get("/api/v1/sessions").param("from", "2026-08-01").param("to", "2026-08-31").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[*].date", everyItem(equalTo("2026-08-06"))))
    }

    @Test
    fun `GET sessions id 호출시 다른 유저 소유 세션이면 404 반환`() {
        val (ownerToken, _) = issueTokenForNewUser()
        val (otherToken, _) = issueTokenForNewUser()
        val created = mockMvc.perform(post("/api/v1/sessions").header("Authorization", "Bearer $ownerToken").contentType(MediaType.APPLICATION_JSON).content("""{"date":"2026-08-06","templateId":null}"""))
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val sessionId = objectMapper.readTree(created).get("id").asText()

        mockMvc.perform(get("/api/v1/sessions/$sessionId").header("Authorization", "Bearer $otherToken"))
            .andExpect(status().isNotFound)
    }
}
