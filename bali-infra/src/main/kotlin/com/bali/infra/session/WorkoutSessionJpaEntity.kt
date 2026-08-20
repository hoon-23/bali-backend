package com.bali.infra.session

import com.bali.core.session.SessionStatus
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.UUID

// WorkoutSession 도메인 모델 영속성을 위한 JPA 엔티티 (logs는 별도 테이블/엔티티로 관리)
@Entity
@Table(name = "sessions")
class WorkoutSessionJpaEntity(
    @Id
    var id: UUID = UUID.randomUUID(),
    var userId: UUID = UUID.randomUUID(),
    var date: LocalDate = LocalDate.now(),
    var templateId: UUID? = null,
    @Enumerated(EnumType.STRING)
    var status: SessionStatus = SessionStatus.SCHEDULED,
    var perceivedDifficulty: Int? = null,
)
