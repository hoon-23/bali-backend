package com.bali.api.exercise

import com.bali.api.auth.jwt.JwtTokenProvider
import com.bali.core.exercise.ExerciseType
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
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExerciseControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jwtTokenProvider: JwtTokenProvider

    @Autowired
    lateinit var userJpaRepository: UserJpaRepository

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var templateRepository: WorkoutTemplateRepository

    // 테스트용 사용자를 만들고 그 사용자의 JWT를 발급
    private fun issueTokenForNewUser(): String {
        val entity = userJpaRepository.save(
            UserJpaEntity(
                email = "exercise-test@example.com",
                provider = AuthProvider.GOOGLE,
                providerId = "sub-exercise-${System.nanoTime()}",
            )
        )
        return jwtTokenProvider.generateToken(entity.id, entity.email)
    }

    // 인증 토큰 없이 GET /api/v1/exercises 호출시 401 Unauthorized를 반환하는지 확인
    @Test
    fun `토큰 없이 GET exercises 호출하면 401 반환`() {
        mockMvc.perform(get("/api/v1/exercises"))
            .andExpect(status().isUnauthorized)
    }

    // 유효한 토큰으로 GET /api/v1/exercises 호출시 시드된 글로벌 카탈로그를 반환하는지 확인
    @Test
    fun `유효한 토큰으로 GET exercises 호출하면 글로벌 카탈로그 반환`() {
        val token = issueTokenForNewUser()

        val response = mockMvc.perform(get("/api/v1/exercises").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val exercises = objectMapper.readTree(response)
        assertTrue(exercises.size() >= 89)
    }

    // muscleGroup 쿼리 파라미터로 필터링했을 때 해당 부위 종목만 반환하는지 확인
    @Test
    fun `muscleGroup 필터로 GET exercises 호출하면 해당 부위만 반환`() {
        val token = issueTokenForNewUser()

        mockMvc.perform(
            get("/api/v1/exercises")
                .param("muscleGroup", "BICEPS")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[*].muscleGroup", everyItem(equalTo("BICEPS"))))
    }

    // 오타를 포함한 쿼리로 suggest 호출시 유사한 글로벌 종목이 결과에 포함되는지 확인
    @Test
    fun `GET exercises suggest 호출하면 오타에 가까운 종목을 제안`() {
        val token = issueTokenForNewUser()

        mockMvc.perform(
            get("/api/v1/exercises/suggest")
                .param("q", "바벨로")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[*].name", hasItem("바벨로우")))
    }

    // POST /api/v1/exercises 호출시 인증된 사용자의 PERSONAL 종목으로 등록되는지 확인
    @Test
    fun `POST exercises 호출하면 인증 사용자의 PERSONAL 종목으로 등록`() {
        val token = issueTokenForNewUser()
        val body = """{"name":"나만의종목","variant":null,"muscleGroup":"BACK","type":"STRENGTH"}"""

        mockMvc.perform(
            post("/api/v1/exercises")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("나만의종목"))
            .andExpect(jsonPath("$.scope").value("PERSONAL"))
    }

    // POST /api/v1/exercises 호출시 name이 공백이면 400을 반환하는지 확인
    @Test
    fun `POST exercises 호출시 name이 공백이면 400 반환`() {
        val token = issueTokenForNewUser()
        val body = """{"name":"   ","variant":null,"muscleGroup":"BACK","type":"STRENGTH"}"""

        mockMvc.perform(
            post("/api/v1/exercises")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isBadRequest)
    }

    // POST /api/v1/exercises 호출시 name이 255자를 초과하면 400을 반환하는지 확인
    @Test
    fun `POST exercises 호출시 name이 255자 초과면 400 반환`() {
        val token = issueTokenForNewUser()
        val tooLongName = "가".repeat(256)
        val body = objectMapper.writeValueAsString(
            ExerciseCreateRequest(
                name = tooLongName,
                variant = null,
                muscleGroup = com.bali.core.exercise.MuscleGroup.BACK,
                type = com.bali.core.exercise.ExerciseType.STRENGTH,
            )
        )

        mockMvc.perform(
            post("/api/v1/exercises")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isBadRequest)
    }

    // 새 개인 종목을 등록하고 그 id를 반환하는 테스트 헬퍼
    private fun createPersonalExercise(token: String, name: String = "원본종목"): String {
        val body = """{"name":"$name","variant":null,"muscleGroup":"BACK","type":"STRENGTH"}"""
        val response = mockMvc.perform(
            post("/api/v1/exercises").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(body)
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return objectMapper.readTree(response).get("id").asText()
    }

    // GLOBAL 카탈로그에서 종목 하나의 id를 가져오는 테스트 헬퍼
    private fun aGlobalExerciseId(token: String): String {
        val response = mockMvc.perform(get("/api/v1/exercises").header("Authorization", "Bearer $token")).andExpect(status().isOk).andReturn().response.contentAsString
        return objectMapper.readTree(response).first().get("id").asText()
    }

    @Test
    fun `PUT exercises id 호출하면 본인 소유 PERSONAL 종목의 name variant muscleGroup을 수정한다`() {
        val token = issueTokenForNewUser()
        val exerciseId = createPersonalExercise(token)

        mockMvc.perform(
            put("/api/v1/exercises/$exerciseId").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"수정된종목","variant":"바벨","muscleGroup":"CHEST"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("수정된종목"))
            .andExpect(jsonPath("$.variant").value("바벨"))
            .andExpect(jsonPath("$.muscleGroup").value("CHEST"))
    }

    @Test
    fun `PUT exercises id 호출시 GLOBAL 종목이면 404 반환한다`() {
        val token = issueTokenForNewUser()
        val globalId = aGlobalExerciseId(token)

        mockMvc.perform(
            put("/api/v1/exercises/$globalId").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"수정시도","variant":null,"muscleGroup":"BACK"}""")
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `PUT exercises id 호출시 다른 유저 소유 PERSONAL 종목이면 404 반환한다`() {
        val ownerToken = issueTokenForNewUser()
        val otherToken = issueTokenForNewUser()
        val exerciseId = createPersonalExercise(ownerToken)

        mockMvc.perform(
            put("/api/v1/exercises/$exerciseId").header("Authorization", "Bearer $otherToken").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"수정시도","variant":null,"muscleGroup":"BACK"}""")
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `PUT exercises id 호출시 name이 공백이면 400 반환한다`() {
        val token = issueTokenForNewUser()
        val exerciseId = createPersonalExercise(token)

        mockMvc.perform(
            put("/api/v1/exercises/$exerciseId").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"   ","variant":null,"muscleGroup":"BACK"}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `DELETE exercises id 호출하면 참조 없는 본인 PERSONAL 종목을 삭제하고 204를 반환한다`() {
        val token = issueTokenForNewUser()
        val exerciseId = createPersonalExercise(token, "삭제될종목")

        mockMvc.perform(delete("/api/v1/exercises/$exerciseId").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/v1/exercises").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$[*].id", not(hasItem(exerciseId))))
    }

    @Test
    fun `DELETE exercises id 호출시 활성 템플릿이 참조 중이면 409를 반환하고 삭제되지 않는다`() {
        val token = issueTokenForNewUser()
        val exerciseId = createPersonalExercise(token, "참조되는종목")
        val userId = jwtTokenProvider.validateAndGetUserId(token)!!
        templateRepository.save(
            WorkoutTemplate(
                id = null, userId = userId, category = TemplateCategory.PUSH, name = "참조템플릿",
                items = listOf(
                    TemplateItem.create(
                        exerciseType = ExerciseType.STRENGTH, exerciseId = java.util.UUID.fromString(exerciseId), sortOrder = 0,
                        targetSets = 3, targetReps = 10, targetWeight = BigDecimal("60.0"),
                    )
                ),
            )
        )

        mockMvc.perform(delete("/api/v1/exercises/$exerciseId").header("Authorization", "Bearer $token"))
            .andExpect(status().isConflict)

        mockMvc.perform(get("/api/v1/exercises").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$[*].id", hasItem(exerciseId)))
    }

    @Test
    fun `DELETE exercises id 호출시 GLOBAL 종목이면 404 반환한다`() {
        val token = issueTokenForNewUser()
        val globalId = aGlobalExerciseId(token)

        mockMvc.perform(delete("/api/v1/exercises/$globalId").header("Authorization", "Bearer $token"))
            .andExpect(status().isNotFound)
    }
}
