package com.bali.infra.exercise

import com.bali.core.exercise.Exercise
import com.bali.core.exercise.ExerciseScope
import com.bali.core.exercise.ExerciseType
import com.bali.core.exercise.MuscleGroup
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
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = [InfraTestConfig::class])
@Import(ExerciseRepositoryAdapter::class)
class ExerciseRepositoryAdapterTest {

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
    lateinit var adapter: ExerciseRepositoryAdapter

    @Test
    fun `findVisibleTo returns at least the seeded global catalog`() {
        val visible = adapter.findVisibleTo(UUID.randomUUID())

        assertTrue(visible.size >= 89)
        assertTrue(visible.all { it.scope == ExerciseScope.GLOBAL })
    }

    @Test
    fun `suggest ranks a typo close to the correct global exercise`() {
        val suggestions = adapter.suggest("바벨로", UUID.randomUUID(), 10)

        assertTrue(suggestions.any { it.name == "바벨로우" })
    }

    // pg_trgm은 2~3글자 한글 짧은 단어를 자모 분해 없이 음절 단위로 트라이그램화하기 때문에
    // "밴치"(오타)와 "벤치프레스"의 유사도가 정확히 0.0이 되어 어떤 임계값으로도 잡히지 않는다.
    // 이 한계를 문서화하고, 추후 접근법이 바뀌면 이 테스트가 깨져 변경을 알리도록 한다.
    @Test
    fun `suggest는 짧은 한글 오타(밴치-벤치프레스)를 trigram 한계로 인해 잡지 못한다`() {
        val suggestions = adapter.suggest("밴치", UUID.randomUUID(), 10)

        assertTrue(suggestions.none { it.name == "벤치프레스" })
    }

    @Test
    fun `save then findById returns a personal exercise with the same owner`() {
        val ownerId = UUID.randomUUID()
        val saved = adapter.save(
            Exercise(
                id = null,
                name = "테스트종목",
                variant = null,
                muscleGroup = MuscleGroup.BACK,
                type = ExerciseType.STRENGTH,
                scope = ExerciseScope.PERSONAL,
                ownerId = ownerId,
            )
        )

        val found = adapter.findById(saved.id!!)

        assertEquals("테스트종목", found?.name)
        assertEquals(ownerId, found?.ownerId)
    }

    @Test
    fun `findVisibleTo includes own personal exercise but not another user's`() {
        val ownerId = UUID.randomUUID()
        val otherOwnerId = UUID.randomUUID()
        adapter.save(
            Exercise(
                id = null, name = "내전용종목", variant = null, muscleGroup = MuscleGroup.LEGS,
                type = ExerciseType.STRENGTH, scope = ExerciseScope.PERSONAL, ownerId = ownerId,
            )
        )
        adapter.save(
            Exercise(
                id = null, name = "남의전용종목", variant = null, muscleGroup = MuscleGroup.LEGS,
                type = ExerciseType.STRENGTH, scope = ExerciseScope.PERSONAL, ownerId = otherOwnerId,
            )
        )

        val visible = adapter.findVisibleTo(ownerId)

        assertTrue(visible.any { it.name == "내전용종목" })
        assertTrue(visible.none { it.name == "남의전용종목" })
    }

    @Test
    fun `suggest includes own personal exercise but not another user's`() {
        val ownerId = UUID.randomUUID()
        val otherOwnerId = UUID.randomUUID()
        adapter.save(
            Exercise(
                id = null, name = "테스트전용종목", variant = null, muscleGroup = MuscleGroup.LEGS,
                type = ExerciseType.STRENGTH, scope = ExerciseScope.PERSONAL, ownerId = ownerId,
            )
        )
        adapter.save(
            Exercise(
                id = null, name = "테스트전용종목", variant = null, muscleGroup = MuscleGroup.LEGS,
                type = ExerciseType.STRENGTH, scope = ExerciseScope.PERSONAL, ownerId = otherOwnerId,
            )
        )

        val suggestions = adapter.suggest("테스트전용종목", ownerId, 10)

        assertTrue(suggestions.any { it.name == "테스트전용종목" && it.ownerId == ownerId })
        assertTrue(suggestions.none { it.name == "테스트전용종목" && it.ownerId == otherOwnerId })
    }
}
