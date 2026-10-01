package com.bali.api.template

import com.bali.api.auth.jwt.JwtTokenProvider
import com.bali.core.exercise.Exercise
import com.bali.core.exercise.ExerciseRepository
import com.bali.core.exercise.ExerciseScope
import com.bali.core.exercise.ExerciseType
import com.bali.core.exercise.MuscleGroup
import com.bali.core.session.SessionStatus
import com.bali.core.session.WorkoutSession
import com.bali.core.session.WorkoutSessionRepository
import com.bali.core.user.AuthProvider
import com.bali.infra.user.UserJpaEntity
import com.bali.infra.user.UserJpaRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
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
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TemplateControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired lateinit var userJpaRepository: UserJpaRepository
    @Autowired lateinit var exerciseRepository: ExerciseRepository
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var sessionRepository: WorkoutSessionRepository

    // 테스트용 사용자를 만들고 그 사용자의 JWT를 발급
    private fun issueTokenForNewUser(): String {
        val entity = userJpaRepository.save(
            UserJpaEntity(email = "template-test-${System.nanoTime()}@example.com", provider = AuthProvider.GOOGLE, providerId = "sub-template-${System.nanoTime()}")
        )
        return jwtTokenProvider.generateToken(entity.id, entity.email)
    }

    // 테스트용 STRENGTH 종목을 저장하고 id 반환
    private fun savedStrengthExerciseId(): UUID =
        exerciseRepository.save(
            Exercise(id = null, name = "템플릿테스트벤치프레스", variant = null, muscleGroup = MuscleGroup.CHEST, type = ExerciseType.STRENGTH, scope = ExerciseScope.GLOBAL, ownerId = null)
        ).id!!

    // 테스트용 사용자를 만들고 (JWT, userId)를 반환
    private fun issueTokenAndUserId(): Pair<String, UUID> {
        val entity = userJpaRepository.save(
            UserJpaEntity(email = "template-test-${System.nanoTime()}@example.com", provider = AuthProvider.GOOGLE, providerId = "sub-template-${System.nanoTime()}")
        )
        return jwtTokenProvider.generateToken(entity.id, entity.email) to entity.id
    }

    // 테스트용 STRENGTH 템플릿을 POST로 만들고 id 반환
    private fun createTemplate(token: String, exerciseId: UUID, name: String): UUID {
        val body = """{"category":"PUSH","name":"$name","items":[{"exerciseId":"$exerciseId","sortOrder":0,"targetSets":3,"targetReps":10,"targetWeight":60.0,"targetDurationSeconds":null,"targetPace":null}]}"""
        val response = mockMvc.perform(post("/api/v1/templates").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString
        return UUID.fromString(objectMapper.readTree(response)["id"].asText())
    }

    // 템플릿으로 만든 세션을 지정 상태/날짜로 저장
    private fun saveSession(userId: UUID, templateId: UUID, date: LocalDate, status: SessionStatus) {
        sessionRepository.save(WorkoutSession(id = null, userId = userId, date = date, templateId = templateId, status = status, logs = emptyList()))
    }

    @Test
    fun `GET templates 목록의 lastUsedAt은 해당 템플릿의 가장 최근 완료 세션 날짜다`() {
        val (token, userId) = issueTokenAndUserId()
        val exerciseId = savedStrengthExerciseId()
        val used = createTemplate(token, exerciseId, "사용한루틴")
        val unused = createTemplate(token, exerciseId, "안쓴루틴")
        val onlyScheduled = createTemplate(token, exerciseId, "예정만있는루틴")
        saveSession(userId, used, LocalDate.of(2026, 9, 20), SessionStatus.COMPLETED)
        saveSession(userId, used, LocalDate.of(2026, 9, 28), SessionStatus.COMPLETED)
        saveSession(userId, used, LocalDate.of(2026, 10, 5), SessionStatus.SCHEDULED)
        saveSession(userId, onlyScheduled, LocalDate.of(2026, 10, 1), SessionStatus.SCHEDULED)

        val response = mockMvc.perform(get("/api/v1/templates").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val byId = objectMapper.readTree(response).associateBy { it["id"].asText() }

        assertEquals("2026-09-28", byId.getValue(used.toString())["lastUsedAt"].asText())
        assertTrue(byId.getValue(unused.toString())["lastUsedAt"].isNull)
        assertTrue(byId.getValue(onlyScheduled.toString())["lastUsedAt"].isNull)
    }

    @Test
    fun `GET templates id 응답에도 lastUsedAt이 포함된다`() {
        val (token, userId) = issueTokenAndUserId()
        val used = createTemplate(token, savedStrengthExerciseId(), "단건조회루틴")
        saveSession(userId, used, LocalDate.of(2026, 9, 28), SessionStatus.COMPLETED)

        mockMvc.perform(get("/api/v1/templates/$used").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.lastUsedAt").value("2026-09-28"))
    }

    @Test
    fun `POST templates 호출하면 STRENGTH 종목이 포함된 템플릿을 생성한다`() {
        val token = issueTokenForNewUser()
        val exerciseId = savedStrengthExerciseId()
        val body = """{"category":"PUSH","name":"내 푸시데이","items":[{"exerciseId":"$exerciseId","sortOrder":0,"targetSets":3,"targetReps":10,"targetWeight":60.0,"targetDurationSeconds":null,"targetPace":null}]}"""

        mockMvc.perform(post("/api/v1/templates").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("내 푸시데이"))
            .andExpect(jsonPath("$.items[0].targetSets").value(3))
    }

    @Test
    fun `POST templates 호출시 STRENGTH 종목에 targetWeight가 없으면 400 반환`() {
        val token = issueTokenForNewUser()
        val exerciseId = savedStrengthExerciseId()
        val body = """{"category":"PUSH","name":"잘못된템플릿","items":[{"exerciseId":"$exerciseId","sortOrder":0,"targetSets":3,"targetReps":10,"targetWeight":null,"targetDurationSeconds":null,"targetPace":null}]}"""

        mockMvc.perform(post("/api/v1/templates").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET templates id 호출시 다른 유저 소유 템플릿이면 404 반환`() {
        val ownerToken = issueTokenForNewUser()
        val otherToken = issueTokenForNewUser()
        val exerciseId = savedStrengthExerciseId()
        val body = """{"category":"PUSH","name":"남의템플릿","items":[{"exerciseId":"$exerciseId","sortOrder":0,"targetSets":3,"targetReps":10,"targetWeight":60.0,"targetDurationSeconds":null,"targetPace":null}]}"""

        val created = mockMvc.perform(post("/api/v1/templates").header("Authorization", "Bearer $ownerToken").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val templateId = objectMapper.readTree(created).get("id").asText()

        mockMvc.perform(get("/api/v1/templates/$templateId").header("Authorization", "Bearer $otherToken"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `PUT templates id 호출하면 items를 전체 교체한다`() {
        val token = issueTokenForNewUser()
        val exerciseId = savedStrengthExerciseId()
        val createBody = """{"category":"PUSH","name":"원본","items":[{"exerciseId":"$exerciseId","sortOrder":0,"targetSets":3,"targetReps":10,"targetWeight":60.0,"targetDurationSeconds":null,"targetPace":null}]}"""
        val created = mockMvc.perform(post("/api/v1/templates").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(createBody))
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val templateId = objectMapper.readTree(created).get("id").asText()

        val updateBody = """{"category":"PULL","name":"수정됨","items":[{"exerciseId":"$exerciseId","sortOrder":0,"targetSets":5,"targetReps":5,"targetWeight":80.0,"targetDurationSeconds":null,"targetPace":null}]}"""
        mockMvc.perform(put("/api/v1/templates/$templateId").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(updateBody))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("수정됨"))
            .andExpect(jsonPath("$.items[0].targetSets").value(5))
            .andExpect(jsonPath("$.items.length()").value(1))
    }

    // Fix 4 회귀 테스트: 다른 유저의 PERSONAL 종목 id를 참조하면 미존재와 동일하게 400 처리돼야 한다 (존재 노출 방지)
    @Test
    fun `POST templates 호출시 다른 유저의 PERSONAL 종목을 참조하면 400 반환`() {
        val userAToken = issueTokenForNewUser()
        val userBEntity = userJpaRepository.save(
            UserJpaEntity(email = "template-test-userB@example.com", provider = AuthProvider.GOOGLE, providerId = "sub-template-b-${System.nanoTime()}")
        )
        val userBPersonalExerciseId = exerciseRepository.save(
            Exercise(id = null, name = "B의개인종목", variant = null, muscleGroup = MuscleGroup.CHEST, type = ExerciseType.STRENGTH, scope = ExerciseScope.PERSONAL, ownerId = userBEntity.id)
        ).id!!

        val body = """{"category":"PUSH","name":"침입시도","items":[{"exerciseId":"$userBPersonalExerciseId","sortOrder":0,"targetSets":3,"targetReps":10,"targetWeight":60.0,"targetDurationSeconds":null,"targetPace":null}]}"""

        mockMvc.perform(post("/api/v1/templates").header("Authorization", "Bearer $userAToken").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `DELETE templates id 호출하면 목록 조회에서 제외된다`() {
        val token = issueTokenForNewUser()
        val exerciseId = savedStrengthExerciseId()
        val createBody = """{"category":"PUSH","name":"삭제될템플릿","items":[{"exerciseId":"$exerciseId","sortOrder":0,"targetSets":3,"targetReps":10,"targetWeight":60.0,"targetDurationSeconds":null,"targetPace":null}]}"""
        val created = mockMvc.perform(post("/api/v1/templates").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(createBody))
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val templateId = objectMapper.readTree(created).get("id").asText()

        mockMvc.perform(delete("/api/v1/templates/$templateId").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/v1/templates").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$[?(@.id=='$templateId')]").isEmpty)
    }
}
