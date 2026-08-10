package com.bali.infra.template

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface TemplateItemJpaRepository : JpaRepository<TemplateItemJpaEntity, UUID> {
    // 특정 템플릿에 속한 모든 항목을 sortOrder 오름차순으로 조회
    fun findByTemplateIdOrderBySortOrderAsc(templateId: UUID): List<TemplateItemJpaEntity>

    // 전체 교체(PUT)를 위해 기존 항목을 모두 삭제
    @Modifying
    @Query("DELETE FROM TemplateItemJpaEntity i WHERE i.templateId = :templateId")
    fun deleteByTemplateId(@Param("templateId") templateId: UUID)

    // exerciseId가 소프트 삭제되지 않은 템플릿의 template_items에서 참조되고 있는지 확인.
    // template_items/templates 사이에 JPA 연관관계가 없어 조인이 필요하므로 네이티브 쿼리로 작성
    @Query(
        value = """
            SELECT EXISTS (
                SELECT 1 FROM template_items ti
                JOIN templates t ON ti.template_id = t.id
                WHERE ti.exercise_id = :exerciseId AND t.deleted = false
            )
        """,
        nativeQuery = true,
    )
    fun existsActiveReferenceToExercise(@Param("exerciseId") exerciseId: UUID): Boolean
}
