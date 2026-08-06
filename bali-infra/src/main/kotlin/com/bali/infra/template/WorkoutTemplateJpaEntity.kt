package com.bali.infra.template

import com.bali.core.template.TemplateCategory
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

// WorkoutTemplate 도메인 모델 영속성을 위한 JPA 엔티티 (items는 별도 테이블/엔티티로 관리)
@Entity
@Table(name = "templates")
class WorkoutTemplateJpaEntity(
    @Id
    var id: UUID = UUID.randomUUID(),
    var userId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    var category: TemplateCategory = TemplateCategory.STRENGTH,
    var name: String = "",
    var deleted: Boolean = false,
)
