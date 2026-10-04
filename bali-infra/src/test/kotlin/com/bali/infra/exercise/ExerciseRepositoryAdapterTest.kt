package com.bali.infra.exercise

import com.bali.core.exercise.Equipment
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

    // 재검토에서 발견된 회귀 버그 재현 케이스: 쿼리 플래너의 row 추정치가 1일 때
    // (예: "레그"처럼 매칭 후보가 적은 짧은 쿼리) FROM절 서브쿼리로 set_limit()을 호출하던
    // 이전 방식은 join 순서가 뒤바뀌어 일부 행이 set_limit(0.2) 적용 전(즉 기본 임계값
    // 상태)에 평가되면서 "레그프레스"(유사도 0.2857, 0.2보다 높아 포함되어야 함)가 간헐적으로
    // 누락됐다. SET LOCAL을 별도 문으로 먼저 실행하는 현재 방식은 이 순서 문제가 없다.
    @Test
    fun `suggest는 레그 쿼리에서 레그프레스를 안정적으로 포함한다 (플래너 순서 버그 회귀 테스트)`() {
        val suggestions = adapter.suggest("레그", UUID.randomUUID(), 10)

        assertTrue(suggestions.any { it.name == "레그프레스" })
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

    @Test
    fun `softDeleteById로 삭제하면 목록과 제안에서 빠지지만 findById는 deleted=true로 조회된다`() {
        val ownerId = UUID.randomUUID()
        val saved = adapter.save(
            Exercise(
                id = null, name = "삭제될종목", variant = null, muscleGroup = MuscleGroup.BACK,
                type = ExerciseType.STRENGTH, scope = ExerciseScope.PERSONAL, ownerId = ownerId,
            )
        )

        adapter.softDeleteById(saved.id!!)

        assertTrue(adapter.findById(saved.id!!)!!.deleted)
        assertTrue(adapter.findVisibleTo(ownerId).none { it.id == saved.id })
        assertTrue(adapter.suggest("삭제될종목", ownerId).none { it.id == saved.id })
    }

    // V26이 시간 버티기 운동 3종을 삭제했는지 확인
    @Test
    fun `V26 이후 버티기 운동 3종은 카탈로그에 없다`() {
        val names = adapter.findVisibleTo(UUID.randomUUID()).map { it.name }

        listOf("플랭크", "사이드플랭크", "할로우홀드").forEach {
            assertTrue(it !in names, "$it 가 아직 카탈로그에 남아 있다")
        }
    }

    // V26이 홈트용 맨몸 12종을 BODYWEIGHT GLOBAL로 시딩했는지 확인
    @Test
    fun `V26 이후 홈트 맨몸 12종이 BODYWEIGHT GLOBAL로 존재한다`() {
        val expected = listOf(
            Triple("버피", null, MuscleGroup.FUNCTIONAL),
            Triple("숄더탭", null, MuscleGroup.FUNCTIONAL),
            Triple("푸시업", "무릎", MuscleGroup.CHEST),
            Triple("크런치", null, MuscleGroup.ABS),
            Triple("바이시클크런치", null, MuscleGroup.ABS),
            Triple("리버스크런치", null, MuscleGroup.ABS),
            Triple("레그레이즈", null, MuscleGroup.ABS),
            Triple("파이크푸시업", "무릎", MuscleGroup.SHOULDER),
            Triple("벤치딥", null, MuscleGroup.TRICEPS),
            Triple("푸시업", "클로즈", MuscleGroup.TRICEPS),
            Triple("리버스스노우엔젤", null, MuscleGroup.BACK),
            Triple("프론Y레이즈", null, MuscleGroup.BACK),
        )
        val visible = adapter.findVisibleTo(UUID.randomUUID())

        expected.forEach { (name, variant, group) ->
            val found = visible.singleOrNull { it.name == name && it.variant == variant }
            assertTrue(found != null, "$name/$variant 가 없거나 중복이다")
            assertEquals(group, found!!.muscleGroup)
            assertEquals(Equipment.BODYWEIGHT, found.equipment)
            assertEquals(ExerciseScope.GLOBAL, found.scope)
            assertEquals(ExerciseType.STRENGTH, found.type)
        }
    }
}
