package com.bali.infra.session

import com.bali.core.exercise.ExerciseType
import com.bali.core.session.SessionLog
import com.bali.core.session.WorkoutSession
import com.bali.infra.InfraTestConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

    @Test
    fun `save then findById returns the session with its logs`() {
        val userId = UUID.randomUUID()
        val saved = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 6), templateId = null, logs = listOf(log())))

        val found = adapter.findById(saved.id!!)

        assertEquals(1, found?.logs?.size)
    }

    @Test
    fun `findAllByUserId only returns sessions within the date range`() {
        val userId = UUID.randomUUID()
        adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 6), templateId = null, logs = emptyList()))
        adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 1, 1), templateId = null, logs = emptyList()))

        val inRange = adapter.findAllByUserId(userId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))

        assertEquals(1, inRange.size)
        assertEquals(LocalDate.of(2026, 8, 6), inRange[0].date)
    }

    @Test
    fun `addLogs appends a new log without touching existing ones`() {
        val userId = UUID.randomUUID()
        val saved = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 6), templateId = null, logs = listOf(log())))

        adapter.addLogs(saved.id!!, listOf(log()))

        assertEquals(2, adapter.findById(saved.id!!)?.logs?.size)
    }

    @Test
    fun `removeLogs deletes only the specified logs`() {
        val userId = UUID.randomUUID()
        val saved = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 6), templateId = null, logs = listOf(log(), log())))
        val logIdToRemove = saved.logs[0].id!!

        adapter.removeLogs(saved.id!!, listOf(logIdToRemove))

        val remaining = adapter.findById(saved.id!!)?.logs
        assertEquals(1, remaining?.size)
        assertTrue(remaining?.none { it.id == logIdToRemove } ?: false)
    }

    @Test
    fun `recordActual sets actual values and completed, leaving unspecified fields unchanged`() {
        val userId = UUID.randomUUID()
        val saved = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 6), templateId = null, logs = listOf(log())))
        val logId = saved.logs[0].id!!

        val updated = adapter.recordActual(
            logId = logId, completed = true,
            actualSets = 3, actualReps = 10, actualWeight = BigDecimal("60.0"),
            actualDurationSeconds = null, actualPace = null,
        )

        assertEquals(true, updated?.completed)
        assertEquals(3, updated?.actualSets)
        assertNull(updated?.actualDurationSeconds)
    }
}
