package com.bali.infra.analysis

import com.bali.core.analysis.AnalysisStatus
import com.bali.core.analysis.AnalysisSummary
import com.bali.core.analysis.Insight
import com.bali.core.analysis.WeeklyAnalysis
import com.bali.core.exercise.MuscleGroup
import com.bali.infra.InfraTestConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
@Import(WeeklyAnalysisRepositoryAdapter::class)
class WeeklyAnalysisRepositoryAdapterTest {

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
    lateinit var adapter: WeeklyAnalysisRepositoryAdapter

    private fun summary(exerciseId: UUID = UUID.randomUUID()) = AnalysisSummary(
        totalWorkoutMinutes = 60,
        volumeByExercise = mapOf(exerciseId to BigDecimal("1800.0")),
        volumeByMuscleGroup = mapOf(MuscleGroup.CHEST to BigDecimal("1800.0")),
        cardioTotalMinutes = 10,
        completionRate = BigDecimal("80.0"),
        volumeChangeFromLastWeekPercent = BigDecimal("15.0"),
    )

    @Test
    fun `save 후 findByUserIdAndWeekOf는 summary와 insights를 함께 반환한다`() {
        val userId = UUID.randomUUID()
        val exerciseId = UUID.randomUUID()
        val analysis = WeeklyAnalysis(
            id = null, userId = userId, weekOf = LocalDate.of(2026, 8, 3), status = AnalysisStatus.SUCCESS,
            summary = summary(exerciseId), insights = listOf(Insight(id = null, summaryText = "완료율이 낮아요")),
        )

        adapter.save(analysis)
        val found = adapter.findByUserIdAndWeekOf(userId, LocalDate.of(2026, 8, 3))

        assertEquals(BigDecimal("80.0"), found?.summary?.completionRate)
        // Map<UUID, BigDecimal>/Map<MuscleGroup, BigDecimal>가 JSONB round-trip 후에도 키/값을 정확히 보존하는지 확인
        assertEquals(BigDecimal("1800.0"), found?.summary?.volumeByExercise?.get(exerciseId))
        assertEquals(BigDecimal("1800.0"), found?.summary?.volumeByMuscleGroup?.get(MuscleGroup.CHEST))
        assertEquals(listOf("완료율이 낮아요"), found?.insights?.map { it.summaryText })
    }

    @Test
    fun `findByUserIdAndWeekOf는 없으면 null을 반환한다`() {
        val found = adapter.findByUserIdAndWeekOf(UUID.randomUUID(), LocalDate.of(2026, 1, 1))
        assertNull(found)
    }

    @Test
    fun `같은 (userId, weekOf)로 다시 save하면 기존 레코드를 대체한다`() {
        val userId = UUID.randomUUID()
        val weekOf = LocalDate.of(2026, 8, 3)
        adapter.save(WeeklyAnalysis(id = null, userId = userId, weekOf = weekOf, status = AnalysisStatus.SUCCESS, summary = summary(), insights = listOf(Insight(id = null, summaryText = "첫번째"))))

        adapter.save(WeeklyAnalysis(id = null, userId = userId, weekOf = weekOf, status = AnalysisStatus.NO_ACTIVITY, summary = null, insights = emptyList()))

        val found = adapter.findByUserIdAndWeekOf(userId, weekOf)
        assertEquals(AnalysisStatus.NO_ACTIVITY, found?.status)
        assertEquals(true, found?.insights?.isEmpty())
    }

    @Test
    fun `findAllByUserId는 weekOf 내림차순으로 반환한다`() {
        val userId = UUID.randomUUID()
        adapter.save(WeeklyAnalysis(id = null, userId = userId, weekOf = LocalDate.of(2026, 8, 3), status = AnalysisStatus.SUCCESS, summary = summary(), insights = emptyList()))
        adapter.save(WeeklyAnalysis(id = null, userId = userId, weekOf = LocalDate.of(2026, 8, 10), status = AnalysisStatus.SUCCESS, summary = summary(), insights = emptyList()))

        val all = adapter.findAllByUserId(userId)

        assertEquals(listOf(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 3)), all.map { it.weekOf })
    }
}
