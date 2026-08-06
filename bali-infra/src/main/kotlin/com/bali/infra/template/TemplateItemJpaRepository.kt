package com.bali.infra.template

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface TemplateItemJpaRepository : JpaRepository<TemplateItemJpaEntity, UUID> {
    // 특정 템플릿에 속한 모든 항목
    fun findByTemplateId(templateId: UUID): List<TemplateItemJpaEntity>

    // 전체 교체(PUT)를 위해 기존 항목을 모두 삭제
    @Modifying
    @Query("DELETE FROM TemplateItemJpaEntity i WHERE i.templateId = :templateId")
    fun deleteByTemplateId(@Param("templateId") templateId: UUID)
}
