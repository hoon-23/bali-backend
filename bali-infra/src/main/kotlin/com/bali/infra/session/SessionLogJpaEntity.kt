package com.bali.infra.session

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.util.UUID

// SessionLog 도메인 모델 영속성을 위한 JPA 엔티티 (sessionId는 FK 컬럼값으로만 보관, 객체 그래프 관계 없음)
@Entity
@Table(name = "session_logs")
class SessionLogJpaEntity(
    @Id
    var id: UUID = UUID.randomUUID(),
    var sessionId: UUID = UUID.randomUUID(),
    var exerciseId: UUID = UUID.randomUUID(),
    var sortOrder: Int = 0,
    var completed: Boolean = false,
    var targetSets: Int? = null,
    var targetReps: Int? = null,
    var targetWeight: BigDecimal? = null,
    var targetDurationSeconds: Int? = null,
    var targetPace: String? = null,
    var actualSets: Int? = null,
    var actualReps: Int? = null,
    var actualWeight: BigDecimal? = null,
    var actualDurationSeconds: Int? = null,
    var actualPace: String? = null,
    // 세트별 시작/종료 시각 리스트. JSON 직렬화된 문자열로 저장 (WeeklyAnalysisJpaEntity.summary와 동일 패턴)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var setTimings: String? = null,
)
