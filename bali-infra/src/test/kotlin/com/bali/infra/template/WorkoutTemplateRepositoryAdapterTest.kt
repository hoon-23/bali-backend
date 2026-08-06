package com.bali.infra.template

import com.bali.core.template.TemplateCategory
import com.bali.core.template.TemplateItem
import com.bali.core.template.WorkoutTemplate
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
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = [InfraTestConfig::class])
@Import(WorkoutTemplateRepositoryAdapter::class)
class WorkoutTemplateRepositoryAdapterTest {

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
    lateinit var adapter: WorkoutTemplateRepositoryAdapter

    private fun templateItem(exerciseId: UUID = UUID.randomUUID(), sortOrder: Int = 0) = TemplateItem.create(
        exerciseType = com.bali.core.exercise.ExerciseType.STRENGTH,
        exerciseId = exerciseId, sortOrder = sortOrder,
        targetSets = 3, targetReps = 10, targetWeight = BigDecimal("60.0"),
    )

    @Test
    fun `save then findById returns the template with its items`() {
        val userId = UUID.randomUUID()
        val saved = adapter.save(
            WorkoutTemplate(id = null, userId = userId, category = TemplateCategory.PUSH, name = "테스트템플릿", items = listOf(templateItem()))
        )

        val found = adapter.findById(saved.id!!)

        assertEquals("테스트템플릿", found?.name)
        assertEquals(1, found?.items?.size)
    }

    @Test
    fun `findAllByUserId excludes another user's templates`() {
        val userId = UUID.randomUUID()
        val otherUserId = UUID.randomUUID()
        adapter.save(WorkoutTemplate(id = null, userId = userId, category = TemplateCategory.PUSH, name = "내템플릿", items = listOf(templateItem())))
        adapter.save(WorkoutTemplate(id = null, userId = otherUserId, category = TemplateCategory.PUSH, name = "남의템플릿", items = listOf(templateItem())))

        val visible = adapter.findAllByUserId(userId)

        assertTrue(visible.any { it.name == "내템플릿" })
        assertTrue(visible.none { it.name == "남의템플릿" })
    }

    @Test
    fun `save with an existing id replaces all items`() {
        val userId = UUID.randomUUID()
        val saved = adapter.save(
            WorkoutTemplate(id = null, userId = userId, category = TemplateCategory.PUSH, name = "원본", items = listOf(templateItem(), templateItem()))
        )

        val replaced = adapter.save(saved.copy(name = "수정됨", items = listOf(templateItem())))

        val found = adapter.findById(replaced.id!!)
        assertEquals("수정됨", found?.name)
        assertEquals(1, found?.items?.size)
    }

    @Test
    fun `softDelete excludes the template from findAllByUserId and findById`() {
        val userId = UUID.randomUUID()
        val saved = adapter.save(
            WorkoutTemplate(id = null, userId = userId, category = TemplateCategory.PUSH, name = "삭제될템플릿", items = listOf(templateItem()))
        )

        adapter.softDelete(saved.id!!)

        assertTrue(adapter.findAllByUserId(userId).none { it.name == "삭제될템플릿" })
        assertEquals(null, adapter.findById(saved.id!!))
    }
}
