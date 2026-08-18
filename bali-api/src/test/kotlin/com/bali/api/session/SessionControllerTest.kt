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
import org.springframework.test.context.transaction.TestTransaction
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
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

    // 테스트용 CARDIO 종목을 저장하고 id 반환
    private fun savedCardioExerciseId(): UUID =
        exerciseRepository.save(
            Exercise(id = null, name = "세션테스트러닝", variant = null, muscleGroup = MuscleGroup.CARDIO, type = ExerciseType.CARDIO, scope = ExerciseScope.GLOBAL, ownerId = null)
        ).id!!

    // STRENGTH 종목의 빈 세션을 만들고 (sessionId, logId)를 반환
    private fun createSessionWithLog(token: String, exerciseId: UUID): Pair<String, String> {
        val created = mockMvc.perform(post("/api/v1/sessions").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content("""{"date":"2026-08-06","templateId":null}"""))
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val sessionId = objectMapper.readTree(created).get("id").asText()
        val addBody = """{"addItems":[{"exerciseId":"$exerciseId","sortOrder":0,"targetSets":null,"targetReps":null,"targetWeight":null,"targetDurationSeconds":null,"targetPace":null}],"updateItems":[],"removeLogIds":[]}"""
        val afterAdd = mockMvc.perform(patch("/api/v1/sessions/$sessionId").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(addBody))
            .andExpect(status().isOk).andReturn().response.contentAsString
        val logId = objectMapper.readTree(afterAdd).get("logs").get(0).get("id").asText()
        return sessionId to logId
    }

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

    @Test
    fun `PATCH sessions id addItems로 즉흥 추가하면 target null이 허용된다`() {
        val (token, _) = issueTokenForNewUser()
        val exerciseId = savedStrengthExerciseId()
        val created = mockMvc.perform(post("/api/v1/sessions").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content("""{"date":"2026-08-06","templateId":null}"""))
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val sessionId = objectMapper.readTree(created).get("id").asText()

        val patchBody = """{"addItems":[{"exerciseId":"$exerciseId","sortOrder":0,"targetSets":null,"targetReps":null,"targetWeight":null,"targetDurationSeconds":null,"targetPace":null}],"updateItems":[],"removeLogIds":[]}"""

        mockMvc.perform(patch("/api/v1/sessions/$sessionId").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(patchBody))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.logs.length()").value(1))
            .andExpect(jsonPath("$.logs[0].targetSets").value(org.hamcrest.Matchers.nullValue()))
    }

    @Test
    fun `PATCH sessions id updateItems로 exerciseId를 바꾸면 같은 logId가 유지된 채 종목만 바뀐다`() {
        val (token, _) = issueTokenForNewUser()
        val originalExerciseId = savedStrengthExerciseId()
        val substituteExerciseId = savedStrengthExerciseId()
        val created = mockMvc.perform(post("/api/v1/sessions").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content("""{"date":"2026-08-06","templateId":null}"""))
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val sessionId = objectMapper.readTree(created).get("id").asText()
        val addBody = """{"addItems":[{"exerciseId":"$originalExerciseId","sortOrder":0,"targetSets":3,"targetReps":10,"targetWeight":60.0,"targetDurationSeconds":null,"targetPace":null}],"updateItems":[],"removeLogIds":[]}"""
        val afterAdd = mockMvc.perform(patch("/api/v1/sessions/$sessionId").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(addBody))
            .andExpect(status().isOk).andReturn().response.contentAsString
        val logId = objectMapper.readTree(afterAdd).get("logs").get(0).get("id").asText()

        val patchBody = """{"addItems":[],"updateItems":[{"logId":"$logId","exerciseId":"$substituteExerciseId","sortOrder":0,"targetSets":5,"targetReps":5,"targetWeight":80.0,"targetDurationSeconds":null,"targetPace":null}],"removeLogIds":[]}"""

        mockMvc.perform(patch("/api/v1/sessions/$sessionId").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(patchBody))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.logs.length()").value(1))
            .andExpect(jsonPath("$.logs[0].id").value(logId))
            .andExpect(jsonPath("$.logs[0].exerciseId").value(substituteExerciseId.toString()))
            .andExpect(jsonPath("$.logs[0].targetSets").value(5))
    }

    @Test
    fun `PATCH sessions id removeLogIds로 삭제하면 목록에서 사라진다`() {
        val (token, _) = issueTokenForNewUser()
        val exerciseId = savedStrengthExerciseId()
        val created = mockMvc.perform(post("/api/v1/sessions").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content("""{"date":"2026-08-06","templateId":null}"""))
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val sessionId = objectMapper.readTree(created).get("id").asText()

        val addBody = """{"addItems":[{"exerciseId":"$exerciseId","sortOrder":0,"targetSets":3,"targetReps":10,"targetWeight":60.0,"targetDurationSeconds":null,"targetPace":null}],"updateItems":[],"removeLogIds":[]}"""
        val afterAdd = mockMvc.perform(patch("/api/v1/sessions/$sessionId").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(addBody))
            .andExpect(status().isOk).andReturn().response.contentAsString
        val logId = objectMapper.readTree(afterAdd).get("logs").get(0).get("id").asText()

        val removeBody = """{"addItems":[],"updateItems":[],"removeLogIds":["$logId"]}"""
        mockMvc.perform(patch("/api/v1/sessions/$sessionId").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(removeBody))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.logs.length()").value(0))
    }

    @Test
    fun `PATCH sessions id updateItems에 세션에 속하지 않는 logId를 넣으면 400 반환`() {
        val (token, _) = issueTokenForNewUser()
        val exerciseId = savedStrengthExerciseId()
        val created = mockMvc.perform(post("/api/v1/sessions").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content("""{"date":"2026-08-06","templateId":null}"""))
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val sessionId = objectMapper.readTree(created).get("id").asText()

        val patchBody = """{"addItems":[],"updateItems":[{"logId":"${UUID.randomUUID()}","exerciseId":"$exerciseId","sortOrder":0,"targetSets":3,"targetReps":10,"targetWeight":60.0,"targetDurationSeconds":null,"targetPace":null}],"removeLogIds":[]}"""

        mockMvc.perform(patch("/api/v1/sessions/$sessionId").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(patchBody))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `PATCH sessions id removeLogIds에 세션에 속하지 않는 logId를 넣으면 400 반환`() {
        val (token, _) = issueTokenForNewUser()
        val created = mockMvc.perform(post("/api/v1/sessions").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content("""{"date":"2026-08-06","templateId":null}"""))
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val sessionId = objectMapper.readTree(created).get("id").asText()

        val patchBody = """{"addItems":[],"updateItems":[],"removeLogIds":["${UUID.randomUUID()}"]}"""

        mockMvc.perform(patch("/api/v1/sessions/$sessionId").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(patchBody))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `PATCH sessions id 같은 요청에서 updateItems 검증에 실패하면 addItems도 커밋되지 않는다`() {
        val (token, _) = issueTokenForNewUser()
        val exerciseId = savedStrengthExerciseId()
        val created = mockMvc.perform(post("/api/v1/sessions").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content("""{"date":"2026-08-06","templateId":null}"""))
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val sessionId = objectMapper.readTree(created).get("id").asText()

        // 클래스 레벨 @Transactional 롤백에 기대지 않고 실제 커밋/롤백 경계를 만들어야
        // PATCH 내부에서 setRollbackOnly()된 트랜잭션의 실제 반영 여부를 관찰할 수 있다
        TestTransaction.flagForCommit()
        TestTransaction.end()
        TestTransaction.start()

        val patchBody = """{"addItems":[{"exerciseId":"$exerciseId","sortOrder":0,"targetSets":3,"targetReps":10,"targetWeight":60.0,"targetDurationSeconds":null,"targetPace":null}],"updateItems":[{"logId":"${UUID.randomUUID()}","exerciseId":"$exerciseId","sortOrder":0,"targetSets":3,"targetReps":10,"targetWeight":60.0,"targetDurationSeconds":null,"targetPace":null}],"removeLogIds":[]}"""

        mockMvc.perform(patch("/api/v1/sessions/$sessionId").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(patchBody))
            .andExpect(status().isBadRequest)

        // PATCH 트랜잭션은 require() 실패로 rollback-only 상태가 됐을 뿐 아직 물리적으로 롤백되지 않았다.
        // flagForCommit() 없이 end()를 호출하면 기본값(롤백)으로 종료되어 addItems insert가 실제로 버려진다
        // (반대로 flagForCommit()을 호출하면 이미 rollback-only인 트랜잭션에 커밋을 시도하다 UnexpectedRollbackException이 발생한다)
        TestTransaction.end()
        TestTransaction.start()

        mockMvc.perform(get("/api/v1/sessions/$sessionId").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.logs.length()").value(0))
    }

    @Test
    fun `PATCH sessions id logs logId 호출하면 actual값과 completed를 기록한다`() {
        val (token, _) = issueTokenForNewUser()
        val exerciseId = savedStrengthExerciseId()
        val created = mockMvc.perform(post("/api/v1/sessions").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content("""{"date":"2026-08-06","templateId":null}"""))
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val sessionId = objectMapper.readTree(created).get("id").asText()
        val addBody = """{"addItems":[{"exerciseId":"$exerciseId","sortOrder":0,"targetSets":null,"targetReps":null,"targetWeight":null,"targetDurationSeconds":null,"targetPace":null}],"updateItems":[],"removeLogIds":[]}"""
        val afterAdd = mockMvc.perform(patch("/api/v1/sessions/$sessionId").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(addBody))
            .andExpect(status().isOk).andReturn().response.contentAsString
        val logId = objectMapper.readTree(afterAdd).get("logs").get(0).get("id").asText()

        val patchLogBody = """{"completed":true,"actualSets":5,"actualReps":8,"actualWeight":70.0,"actualDurationSeconds":null,"actualPace":null}"""
        mockMvc.perform(patch("/api/v1/sessions/$sessionId/logs/$logId").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(patchLogBody))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.completed").value(true))
            .andExpect(jsonPath("$.actualSets").value(5))
    }

    @Test
    fun `PATCH sessions id logs logId 호출시 STRENGTH 종목에 actualDurationSeconds를 넣으면 400 반환`() {
        val (token, _) = issueTokenForNewUser()
        val exerciseId = savedStrengthExerciseId()
        val created = mockMvc.perform(post("/api/v1/sessions").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content("""{"date":"2026-08-06","templateId":null}"""))
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val sessionId = objectMapper.readTree(created).get("id").asText()
        val addBody = """{"addItems":[{"exerciseId":"$exerciseId","sortOrder":0,"targetSets":null,"targetReps":null,"targetWeight":null,"targetDurationSeconds":null,"targetPace":null}],"updateItems":[],"removeLogIds":[]}"""
        val afterAdd = mockMvc.perform(patch("/api/v1/sessions/$sessionId").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(addBody))
            .andExpect(status().isOk).andReturn().response.contentAsString
        val logId = objectMapper.readTree(afterAdd).get("logs").get(0).get("id").asText()

        val patchLogBody = """{"completed":true,"actualSets":null,"actualReps":null,"actualWeight":null,"actualDurationSeconds":600,"actualPace":null}"""
        mockMvc.perform(patch("/api/v1/sessions/$sessionId/logs/$logId").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(patchLogBody))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `PATCH sessions id logs logId 호출하면 setTimings를 저장하고 GET 응답에도 포함된다`() {
        val (token, _) = issueTokenForNewUser()
        val exerciseId = savedStrengthExerciseId()
        val (sessionId, logId) = createSessionWithLog(token, exerciseId)

        val patchLogBody = """
            {"completed":true,"actualSets":3,"actualReps":10,"actualWeight":60.0,"actualDurationSeconds":null,"actualPace":null,
             "setTimings":[
               {"setIndex":0,"startedAt":"2026-08-18T10:00:00Z","endedAt":"2026-08-18T10:00:45Z"},
               {"setIndex":1,"startedAt":"2026-08-18T10:02:10Z","endedAt":"2026-08-18T10:02:58Z"}
             ]}
        """.trimIndent()
        mockMvc.perform(patch("/api/v1/sessions/$sessionId/logs/$logId").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(patchLogBody))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.setTimings.length()").value(2))
            .andExpect(jsonPath("$.setTimings[0].setIndex").value(0))
            .andExpect(jsonPath("$.setTimings[0].startedAt").value("2026-08-18T10:00:00Z"))

        mockMvc.perform(get("/api/v1/sessions/$sessionId").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.logs[0].setTimings.length()").value(2))
    }

    @Test
    fun `PATCH sessions id logs logId 호출시 CARDIO 종목에 setTimings를 넣으면 400 반환`() {
        val (token, _) = issueTokenForNewUser()
        val exerciseId = savedCardioExerciseId()
        val (sessionId, logId) = createSessionWithLog(token, exerciseId)

        val patchLogBody = """
            {"completed":true,"actualSets":null,"actualReps":null,"actualWeight":null,"actualDurationSeconds":600,"actualPace":null,
             "setTimings":[{"setIndex":0,"startedAt":"2026-08-18T10:00:00Z","endedAt":"2026-08-18T10:00:45Z"}]}
        """.trimIndent()
        mockMvc.perform(patch("/api/v1/sessions/$sessionId/logs/$logId").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(patchLogBody))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `PATCH sessions id logs logId 호출시 setTiming의 endedAt이 startedAt보다 빠르면 400 반환`() {
        val (token, _) = issueTokenForNewUser()
        val exerciseId = savedStrengthExerciseId()
        val (sessionId, logId) = createSessionWithLog(token, exerciseId)

        val patchLogBody = """
            {"completed":true,"actualSets":3,"actualReps":10,"actualWeight":60.0,"actualDurationSeconds":null,"actualPace":null,
             "setTimings":[{"setIndex":0,"startedAt":"2026-08-18T10:00:45Z","endedAt":"2026-08-18T10:00:00Z"}]}
        """.trimIndent()
        mockMvc.perform(patch("/api/v1/sessions/$sessionId/logs/$logId").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(patchLogBody))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `PATCH sessions id logs logId 호출시 setTimings를 생략하면 기존 값이 유지된다`() {
        val (token, _) = issueTokenForNewUser()
        val exerciseId = savedStrengthExerciseId()
        val (sessionId, logId) = createSessionWithLog(token, exerciseId)

        val firstPatchBody = """
            {"completed":false,"actualSets":null,"actualReps":null,"actualWeight":null,"actualDurationSeconds":null,"actualPace":null,
             "setTimings":[{"setIndex":0,"startedAt":"2026-08-18T10:00:00Z","endedAt":"2026-08-18T10:00:45Z"}]}
        """.trimIndent()
        mockMvc.perform(patch("/api/v1/sessions/$sessionId/logs/$logId").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(firstPatchBody))
            .andExpect(status().isOk)

        val secondPatchBody = """{"completed":true,"actualSets":3,"actualReps":10,"actualWeight":60.0,"actualDurationSeconds":null,"actualPace":null,"setTimings":null}"""
        mockMvc.perform(patch("/api/v1/sessions/$sessionId/logs/$logId").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(secondPatchBody))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.setTimings.length()").value(1))
    }

    // Task 10의 updateItems(exerciseId 교체)와 이 태스크의 PATCH .../logs/{logId}(actual 기록)가
    // 함께 있어야 검증 가능한 회귀 시나리오: 완료 기록 후 종목을 변형하면 completed/actual이 초기화되는지
    @Test
    fun `PATCH sessions id updateItems로 종목을 변형하면 이전에 기록한 completed와 actual값이 초기화된다`() {
        val (token, userId) = issueTokenForNewUser()
        val originalExerciseId = savedStrengthExerciseId()
        val substituteExerciseId = savedStrengthExerciseId()
        val template = templateRepository.save(
            WorkoutTemplate(
                id = null, userId = userId, category = TemplateCategory.PUSH, name = "템플릿",
                items = listOf(TemplateItem.create(ExerciseType.STRENGTH, originalExerciseId, 0, targetSets = 3, targetReps = 10, targetWeight = BigDecimal("60.0"))),
            )
        )
        val created = mockMvc.perform(post("/api/v1/sessions").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content("""{"date":"2026-08-06","templateId":"${template.id}"}"""))
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val sessionJson = objectMapper.readTree(created)
        val sessionId = sessionJson.get("id").asText()
        val logId = sessionJson.get("logs").get(0).get("id").asText()

        mockMvc.perform(patch("/api/v1/sessions/$sessionId/logs/$logId").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON)
            .content("""{"completed":true,"actualSets":3,"actualReps":10,"actualWeight":60.0,"actualDurationSeconds":null,"actualPace":null}"""))
            .andExpect(status().isOk)

        val patchBody = """{"addItems":[],"updateItems":[{"logId":"$logId","exerciseId":"$substituteExerciseId","sortOrder":0,"targetSets":3,"targetReps":10,"targetWeight":60.0,"targetDurationSeconds":null,"targetPace":null}],"removeLogIds":[]}"""

        mockMvc.perform(patch("/api/v1/sessions/$sessionId").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(patchBody))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.logs[0].exerciseId").value(substituteExerciseId.toString()))
            .andExpect(jsonPath("$.logs[0].completed").value(false))
            .andExpect(jsonPath("$.logs[0].actualSets").value(org.hamcrest.Matchers.nullValue()))
    }

    // Fix 1 회귀 테스트: updateItems에서 exerciseId가 그대로면(sortOrder/target*만 변경) completed/actual*가 보존돼야 한다
    @Test
    fun `PATCH sessions id updateItems로 exerciseId가 그대로인 항목을 수정하면 completed와 actual값이 보존된다`() {
        val (token, userId) = issueTokenForNewUser()
        val exerciseId = savedStrengthExerciseId()
        val template = templateRepository.save(
            WorkoutTemplate(
                id = null, userId = userId, category = TemplateCategory.PUSH, name = "템플릿",
                items = listOf(TemplateItem.create(ExerciseType.STRENGTH, exerciseId, 0, targetSets = 3, targetReps = 10, targetWeight = BigDecimal("60.0"))),
            )
        )
        val created = mockMvc.perform(post("/api/v1/sessions").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content("""{"date":"2026-08-06","templateId":"${template.id}"}"""))
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val sessionJson = objectMapper.readTree(created)
        val sessionId = sessionJson.get("id").asText()
        val logId = sessionJson.get("logs").get(0).get("id").asText()

        mockMvc.perform(patch("/api/v1/sessions/$sessionId/logs/$logId").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON)
            .content("""{"completed":true,"actualSets":3,"actualReps":10,"actualWeight":60.0,"actualDurationSeconds":null,"actualPace":null}"""))
            .andExpect(status().isOk)

        // exerciseId는 동일하게 유지한 채 sortOrder만 변경
        val patchBody = """{"addItems":[],"updateItems":[{"logId":"$logId","exerciseId":"$exerciseId","sortOrder":1,"targetSets":3,"targetReps":10,"targetWeight":60.0,"targetDurationSeconds":null,"targetPace":null}],"removeLogIds":[]}"""

        mockMvc.perform(patch("/api/v1/sessions/$sessionId").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(patchBody))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.logs[0].sortOrder").value(1))
            .andExpect(jsonPath("$.logs[0].completed").value(true))
            .andExpect(jsonPath("$.logs[0].actualSets").value(3))
            .andExpect(jsonPath("$.logs[0].actualReps").value(10))
            .andExpect(jsonPath("$.logs[0].actualWeight").value(60.0))
    }

    @Test
    fun `DELETE sessions id 호출하면 세션을 삭제하고 204를 반환한다`() {
        val (token, _) = issueTokenForNewUser()
        val created = mockMvc.perform(post("/api/v1/sessions").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content("""{"date":"2026-08-06","templateId":null}"""))
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val sessionId = objectMapper.readTree(created).get("id").asText()

        mockMvc.perform(delete("/api/v1/sessions/$sessionId").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/v1/sessions/$sessionId").header("Authorization", "Bearer $token"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `DELETE sessions id 호출시 다른 유저 소유 세션이면 404 반환하고 삭제되지 않는다`() {
        val (ownerToken, _) = issueTokenForNewUser()
        val (otherToken, _) = issueTokenForNewUser()
        val created = mockMvc.perform(post("/api/v1/sessions").header("Authorization", "Bearer $ownerToken").contentType(MediaType.APPLICATION_JSON).content("""{"date":"2026-08-06","templateId":null}"""))
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val sessionId = objectMapper.readTree(created).get("id").asText()

        mockMvc.perform(delete("/api/v1/sessions/$sessionId").header("Authorization", "Bearer $otherToken"))
            .andExpect(status().isNotFound)

        mockMvc.perform(get("/api/v1/sessions/$sessionId").header("Authorization", "Bearer $ownerToken"))
            .andExpect(status().isOk)
    }

    @Test
    fun `DELETE sessions id 호출시 존재하지 않는 id면 404 반환한다`() {
        val (token, _) = issueTokenForNewUser()

        mockMvc.perform(delete("/api/v1/sessions/${UUID.randomUUID()}").header("Authorization", "Bearer $token"))
            .andExpect(status().isNotFound)
    }
}
