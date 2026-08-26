package com.bali.infra.analysis

import com.bali.core.analysis.AnalysisStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDate
import java.util.UUID

// MonthlyAnalysis 도메인 모델 영속성을 위한 JPA 엔티티 (summary는 JSONB 컬럼에 직렬화된 문자열로 저장)
@Entity
@Table(name = "monthly_analyses")
class MonthlyAnalysisJpaEntity(
    @Id
    var id: UUID = UUID.randomUUID(),
    var userId: UUID = UUID.randomUUID(),
    var monthOf: LocalDate = LocalDate.now(),
    @Enumerated(EnumType.STRING)
    var status: AnalysisStatus = AnalysisStatus.NO_ACTIVITY,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var summary: String? = null,
)
