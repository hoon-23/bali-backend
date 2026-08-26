package com.bali.infra.session

import com.bali.core.exercise.ExerciseType
import com.bali.core.session.SessionLog
import com.bali.core.session.SessionStatus
import com.bali.core.session.SetTiming
import com.bali.core.session.WorkoutSession
import com.bali.infra.InfraTestConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = [InfraTestConfig::class])
@Import(WorkoutSessionRepositoryAdapter::class)
class WorkoutSessionRepositoryAdapterTest {

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { "jdbc:postgresql://localhost:5432/bali" }
            registry.add("spring.datasource.username") { "bali" }
            registry.add("spring.datasource.password") { "bali" }
            registry.add("spring.jpa.hibernate.ddl-auto") { "validate" }
        }
    }

    @Autowired
    lateinit var adapter: WorkoutSessionRepositoryAdapter

    private fun log(exerciseId: UUID = UUID.randomUUID()) = SessionLog.create(
        exerciseType = ExerciseType.STRENGTH, exerciseId = exerciseId, sortOrder = 0,
        targetSets = 3, targetReps = 10, targetWeight = BigDecimal("60.0"),
    )

    private fun completedLog(exerciseId: UUID = UUID.randomUUID()) = log(exerciseId).copy(
        completed = true, actualSets = 3, actualReps = 10, actualWeight = BigDecimal("60.0"),
    )

    @Test
    fun `save then findById returns the session with its logs`() {
        val userId = UUID.randomUUID()
        val saved = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 6), templateId = null, status = SessionStatus.SCHEDULED, logs = listOf(log())))

        val found = adapter.findById(saved.id!!)

        assertEquals(1, found?.logs?.size)
    }

    @Test
    fun `findAllByUserId only returns sessions within the date range, each with its own logs`() {
        val userId = UUID.randomUUID()
        val firstExerciseId = UUID.randomUUID()
        val secondExerciseId = UUID.randomUUID()
        val outOfRangeExerciseId = UUID.randomUUID()
        val first = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 6), templateId = null, status = SessionStatus.SCHEDULED, logs = listOf(log(firstExerciseId))))
        val second = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 10), templateId = null, status = SessionStatus.SCHEDULED, logs = listOf(log(secondExerciseId))))
        adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 1, 1), templateId = null, status = SessionStatus.SCHEDULED, logs = listOf(log(outOfRangeExerciseId))))

        val inRange = adapter.findAllByUserId(userId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))

        assertEquals(2, inRange.size)
        val firstResult = inRange.single { it.id == first.id }
        val secondResult = inRange.single { it.id == second.id }
        assertEquals(listOf(firstExerciseId), firstResult.logs.map { it.exerciseId })
        assertEquals(listOf(secondExerciseId), secondResult.logs.map { it.exerciseId })
    }

    @Test
    fun `findPageByUserId는 date DESC 순으로 limit개만 반환하고, 같은 date는 id DESC로 tie-break한다`() {
        val userId = UUID.randomUUID()
        val oldest = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 1), templateId = null, status = SessionStatus.SCHEDULED, logs = emptyList()))
        val sameDateA = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 3), templateId = null, status = SessionStatus.SCHEDULED, logs = emptyList()))
        val sameDateB = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 3), templateId = null, status = SessionStatus.SCHEDULED, logs = emptyList()))
        val expectedOrder = listOf(sameDateA, sameDateB).sortedByDescending { it.id.toString() }.map { it.id }

        val page = adapter.findPageByUserId(userId, from = null, to = null, cursorDate = null, cursorId = null, limit = 2)

        assertEquals(expectedOrder, page.map { it.id })
        assertTrue(page.none { it.id == oldest.id })
    }

    @Test
    fun `findPageByUserId는 cursor 이전 세션만 반환해 이어서 조회할 수 있다`() {
        val userId = UUID.randomUUID()
        val first = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 1), templateId = null, status = SessionStatus.SCHEDULED, logs = emptyList()))
        val second = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 2), templateId = null, status = SessionStatus.SCHEDULED, logs = emptyList()))
        val third = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 3), templateId = null, status = SessionStatus.SCHEDULED, logs = emptyList()))

        // 커서를 third 자신의 (date, id)로 지정: id < 자기 자신은 항상 false이므로 third는 결정적으로 제외된다
        val nextPage = adapter.findPageByUserId(userId, from = null, to = null, cursorDate = third.date, cursorId = third.id!!, limit = 10)

        assertEquals(setOf(first.id, second.id), nextPage.map { it.id }.toSet())
    }

    @Test
    fun `findPageByUserId는 from to 범위와 함께 쓰면 그 범위 내에서만 조회한다`() {
        val userId = UUID.randomUUID()
        val inRange = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 15), templateId = null, status = SessionStatus.SCHEDULED, logs = emptyList()))
        adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 1, 1), templateId = null, status = SessionStatus.SCHEDULED, logs = emptyList()))

        val page = adapter.findPageByUserId(userId, from = LocalDate.of(2026, 8, 1), to = LocalDate.of(2026, 8, 31), cursorDate = null, cursorId = null, limit = 10)

        assertEquals(listOf(inRange.id), page.map { it.id })
    }

    @Test
    fun `addLogs appends a new log without touching existing ones`() {
        val userId = UUID.randomUUID()
        val saved = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 6), templateId = null, status = SessionStatus.SCHEDULED, logs = listOf(log())))

        adapter.addLogs(saved.id!!, listOf(log()))

        assertEquals(2, adapter.findById(saved.id!!)?.logs?.size)
    }

    @Test
    fun `removeLogs deletes only the specified logs`() {
        val userId = UUID.randomUUID()
        val saved = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 6), templateId = null, status = SessionStatus.SCHEDULED, logs = listOf(log(), log())))
        val logIdToRemove = saved.logs[0].id!!

        adapter.removeLogs(saved.id!!, listOf(logIdToRemove))

        val remaining = adapter.findById(saved.id!!)?.logs
        assertEquals(1, remaining?.size)
        assertTrue(remaining?.none { it.id == logIdToRemove } ?: false)
    }

    @Test
    fun `recordActual sets actual values and completed, leaving unspecified fields unchanged`() {
        val userId = UUID.randomUUID()
        val saved = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 6), templateId = null, status = SessionStatus.SCHEDULED, logs = listOf(log())))
        val logId = saved.logs[0].id!!

        // first call establishes a non-null actualDurationSeconds baseline
        adapter.recordActual(
            logId = logId, completed = false,
            actualSets = null, actualReps = null, actualWeight = null,
            actualDurationSeconds = 120, actualPace = null,
            setTimings = null,
        )

        // second call passes actualDurationSeconds = null; if the implementation clobbers
        // instead of skipping null params, the previously-set value of 120 would be lost
        val updated = adapter.recordActual(
            logId = logId, completed = true,
            actualSets = 3, actualReps = 10, actualWeight = BigDecimal("60.0"),
            actualDurationSeconds = null, actualPace = null,
            setTimings = null,
        )

        assertEquals(true, updated?.completed)
        assertEquals(3, updated?.actualSets)
        assertEquals(120, updated?.actualDurationSeconds)
    }

    @Test
    fun `recordActual로 setTimings를 저장하면 JSON round-trip으로 Instant까지 온전히 조회된다`() {
        val userId = UUID.randomUUID()
        val saved = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 6), templateId = null, status = SessionStatus.SCHEDULED, logs = listOf(log())))
        val logId = saved.logs[0].id!!
        val timings = listOf(
            SetTiming(0, Instant.parse("2026-08-18T10:00:00Z"), Instant.parse("2026-08-18T10:00:45Z")),
            SetTiming(1, Instant.parse("2026-08-18T10:02:10Z"), Instant.parse("2026-08-18T10:02:58Z")),
        )

        adapter.recordActual(
            logId = logId, completed = true,
            actualSets = null, actualReps = null, actualWeight = null,
            actualDurationSeconds = null, actualPace = null,
            setTimings = timings,
        )

        val found = adapter.findById(saved.id!!)
        assertEquals(timings, found?.logs?.get(0)?.setTimings)
    }

    @Test
    fun `updateStatus로 status만 갱신되고 존재하지 않는 세션이면 null을 반환한다`() {
        val userId = UUID.randomUUID()
        val saved = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 6), templateId = null, status = SessionStatus.SCHEDULED, logs = listOf(log())))

        val updated = adapter.updateStatus(saved.id!!, SessionStatus.IN_PROGRESS)

        assertEquals(SessionStatus.IN_PROGRESS, updated?.status)
        assertEquals(SessionStatus.IN_PROGRESS, adapter.findById(saved.id!!)?.status)
        assertEquals(null, adapter.updateStatus(UUID.randomUUID(), SessionStatus.COMPLETED))
    }

    @Test
    fun `deleteById로 세션을 삭제하면 소속 logs도 DB cascade로 함께 제거된다`() {
        val userId = UUID.randomUUID()
        val saved = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 6), templateId = null, status = SessionStatus.SCHEDULED, logs = listOf(log())))
        val logId = saved.logs[0].id!!

        adapter.deleteById(saved.id!!)

        assertEquals(null, adapter.findById(saved.id!!))
        assertEquals(null, adapter.findLogById(logId))
    }

    @Test
    fun `findActiveDates는 completed 로그가 있는 날짜만, since 이후만 반환한다`() {
        val userId = UUID.randomUUID()
        val otherUserId = UUID.randomUUID()
        adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 15), templateId = null, status = SessionStatus.SCHEDULED, logs = listOf(completedLog())))
        adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 14), templateId = null, status = SessionStatus.SCHEDULED, logs = listOf(log())))
        adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 1, 1), templateId = null, status = SessionStatus.SCHEDULED, logs = listOf(completedLog())))
        // 다른 사용자의 same-range completed 세션이 결과에 섞여 들어오지 않는지 검증 (userId 필터 미검증 방지)
        adapter.save(WorkoutSession(id = null, userId = otherUserId, date = LocalDate.of(2026, 8, 15), templateId = null, status = SessionStatus.SCHEDULED, logs = listOf(completedLog())))

        val activeDates = adapter.findActiveDates(userId, since = LocalDate.of(2026, 8, 1))

        assertEquals(setOf(LocalDate.of(2026, 8, 15)), activeDates)
    }

    @Test
    fun `findAllByDateAndStatus는 유저 무관하게 날짜+상태가 일치하는 세션을 반환한다`() {
        val date = LocalDate.now()
        val matching = adapter.save(WorkoutSession(id = null, userId = UUID.randomUUID(), date = date, templateId = null, status = SessionStatus.SCHEDULED, logs = emptyList()))
        adapter.save(WorkoutSession(id = null, userId = UUID.randomUUID(), date = date, templateId = null, status = SessionStatus.COMPLETED, logs = emptyList()))
        adapter.save(WorkoutSession(id = null, userId = UUID.randomUUID(), date = date.minusDays(1), templateId = null, status = SessionStatus.SCHEDULED, logs = emptyList()))

        val result = adapter.findAllByDateAndStatus(date, SessionStatus.SCHEDULED)

        assertTrue(result.any { it.id == matching.id })
        assertTrue(result.all { it.date == date && it.status == SessionStatus.SCHEDULED })
    }

    @Test
    fun `findLastActiveDate는 완료된 로그가 있는 가장 최근 날짜를 반환한다`() {
        val userId = UUID.randomUUID()
        val exerciseId = UUID.randomUUID()
        val oldLog = SessionLog.create(ExerciseType.STRENGTH, exerciseId, sortOrder = 0, targetSets = 3, targetReps = 10, targetWeight = BigDecimal("40.0")).copy(completed = true)
        val recentLog = SessionLog.create(ExerciseType.STRENGTH, exerciseId, sortOrder = 0, targetSets = 3, targetReps = 10, targetWeight = BigDecimal("40.0")).copy(completed = true)
        adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.now().minusDays(10), templateId = null, status = SessionStatus.COMPLETED, logs = listOf(oldLog)))
        adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.now().minusDays(2), templateId = null, status = SessionStatus.COMPLETED, logs = listOf(recentLog)))

        assertEquals(LocalDate.now().minusDays(2), adapter.findLastActiveDate(userId))
    }

    @Test
    fun `완료된 로그가 없으면 findLastActiveDate는 null을 반환한다`() {
        assertEquals(null, adapter.findLastActiveDate(UUID.randomUUID()))
    }
}
