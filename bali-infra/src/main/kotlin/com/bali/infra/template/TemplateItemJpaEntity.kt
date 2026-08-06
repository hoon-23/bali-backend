package com.bali.infra.template

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

// TemplateItem 도메인 모델 영속성을 위한 JPA 엔티티 (templateId는 FK 컬럼값으로만 보관, 객체 그래프 관계 없음)
@Entity
@Table(name = "template_items")
class TemplateItemJpaEntity(
    @Id
    var id: UUID = UUID.randomUUID(),
    var templateId: UUID = UUID.randomUUID(),
    var exerciseId: UUID = UUID.randomUUID(),
    var sortOrder: Int = 0,
    var targetSets: Int? = null,
    var targetReps: Int? = null,
    var targetWeight: BigDecimal? = null,
    var targetDurationSeconds: Int? = null,
    var targetPace: String? = null,
)
