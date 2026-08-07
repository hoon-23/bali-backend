package com.bali.infra.analysis

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

// Insight 도메인 모델 영속성을 위한 JPA 엔티티 (analysisId는 FK 컬럼값으로만 보관, 객체 그래프 관계 없음)
@Entity
@Table(name = "insights")
class InsightJpaEntity(
    @Id
    var id: UUID = UUID.randomUUID(),
    var analysisId: UUID = UUID.randomUUID(),
    var summaryText: String = "",
)
