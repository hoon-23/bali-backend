package com.bali.infra.template

import com.bali.core.template.TemplateItem
import com.bali.core.template.WorkoutTemplate
import com.bali.core.template.WorkoutTemplateRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// JPA 백엔드로 WorkoutTemplateRepository 포트를 구현하는 Spring 리포지토리 어댑터
@Repository
class WorkoutTemplateRepositoryAdapter(
    private val templateJpaRepository: WorkoutTemplateJpaRepository,
    private val itemJpaRepository: TemplateItemJpaRepository,
) : WorkoutTemplateRepository {

    // 고유 식별자로 템플릿과 그 items를 함께 조회 (소프트 삭제된 템플릿은 null 반환)
    @Transactional
    override fun findById(id: UUID): WorkoutTemplate? {
        val entity = templateJpaRepository.findById(id).orElse(null) ?: return null
        if (entity.deleted) return null
        return entity.toDomain(itemJpaRepository.findByTemplateIdOrderBySortOrderAsc(id))
    }

    // 특정 유저의 소프트 삭제되지 않은 템플릿 목록을 items와 함께 조회
    @Transactional
    override fun findAllByUserId(userId: UUID): List<WorkoutTemplate> =
        templateJpaRepository.findAllByUserIdAndDeletedFalse(userId).map { entity ->
            entity.toDomain(itemJpaRepository.findByTemplateIdOrderBySortOrderAsc(entity.id))
        }

    // 템플릿을 저장하고 items를 전체 삭제 후 재삽입 (신규 생성과 PUT 전체교체를 동일 로직으로 처리)
    @Transactional
    override fun save(template: WorkoutTemplate): WorkoutTemplate {
        val templateId = template.id ?: UUID.randomUUID()
        templateJpaRepository.save(
            WorkoutTemplateJpaEntity(
                id = templateId,
                userId = template.userId,
                category = template.category,
                name = template.name,
                deleted = template.deleted,
            )
        )
        itemJpaRepository.deleteByTemplateId(templateId)
        val savedItems = template.items.map { item ->
            itemJpaRepository.save(
                TemplateItemJpaEntity(
                    id = item.id ?: UUID.randomUUID(),
                    templateId = templateId,
                    exerciseId = item.exerciseId,
                    sortOrder = item.sortOrder,
                    targetSets = item.targetSets,
                    targetReps = item.targetReps,
                    targetWeight = item.targetWeight,
                    targetDurationSeconds = item.targetDurationSeconds,
                    targetPace = item.targetPace,
                )
            )
        }
        return template.copy(id = templateId, items = savedItems.map { it.toDomain() })
    }

    // 템플릿을 소프트 삭제 (존재하지 않으면 조용히 무시)
    @Transactional
    override fun softDelete(id: UUID) {
        val entity = templateJpaRepository.findById(id).orElse(null) ?: return
        entity.deleted = true
        templateJpaRepository.save(entity)
    }

    // exerciseId가 소프트 삭제되지 않은 템플릿의 items에서 참조되고 있는지 확인
    override fun existsActiveReferenceToExercise(exerciseId: UUID): Boolean =
        itemJpaRepository.existsActiveReferenceToExercise(exerciseId)

    // JPA 엔티티(+items)를 도메인 모델로 변환
    private fun WorkoutTemplateJpaEntity.toDomain(items: List<TemplateItemJpaEntity>) = WorkoutTemplate(
        id = id, userId = userId, category = category, name = name, deleted = deleted,
        items = items.map { it.toDomain() },
    )

    // TemplateItem JPA 엔티티를 도메인 모델로 변환
    private fun TemplateItemJpaEntity.toDomain() = TemplateItem(
        id = id, exerciseId = exerciseId, sortOrder = sortOrder,
        targetSets = targetSets, targetReps = targetReps, targetWeight = targetWeight,
        targetDurationSeconds = targetDurationSeconds, targetPace = targetPace,
    )
}
