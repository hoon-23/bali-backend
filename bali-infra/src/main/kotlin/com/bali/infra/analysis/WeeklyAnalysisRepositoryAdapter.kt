package com.bali.infra.analysis

import com.bali.core.analysis.AnalysisSummary
import com.bali.core.analysis.Insight
import com.bali.core.analysis.WeeklyAnalysis
import com.bali.core.analysis.WeeklyAnalysisRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

// JPA 백엔드로 WeeklyAnalysisRepository 포트를 구현하는 Spring 리포지토리 어댑터
@Repository
class WeeklyAnalysisRepositoryAdapter(
    private val analysisJpaRepository: WeeklyAnalysisJpaRepository,
    private val insightJpaRepository: InsightJpaRepository,
) : WeeklyAnalysisRepository {

    private val objectMapper = jacksonObjectMapper()

    // 특정 유저의 특정 주 분석 결과를 인사이트와 함께 조회
    @Transactional
    override fun findByUserIdAndWeekOf(userId: UUID, weekOf: LocalDate): WeeklyAnalysis? {
        val entity = analysisJpaRepository.findByUserIdAndWeekOf(userId, weekOf) ?: return null
        return entity.toDomain(insightJpaRepository.findByAnalysisId(entity.id))
    }

    // 특정 유저의 전체 분석 결과를 weekOf 내림차순으로, 인사이트와 함께 조회
    @Transactional
    override fun findAllByUserId(userId: UUID): List<WeeklyAnalysis> {
        val entities = analysisJpaRepository.findAllByUserIdOrderByWeekOfDesc(userId)
        val insightsByAnalysisId = insightJpaRepository.findByAnalysisIdIn(entities.map { it.id }).groupBy { it.analysisId }
        return entities.map { it.toDomain(insightsByAnalysisId[it.id] ?: emptyList()) }
    }

    // 분석 결과를 저장 (동일 (userId, weekOf) 기존 레코드가 있으면 삭제 후 재생성)
    @Transactional
    override fun save(analysis: WeeklyAnalysis): WeeklyAnalysis {
        analysisJpaRepository.findByUserIdAndWeekOf(analysis.userId, analysis.weekOf)?.let { existing ->
            insightJpaRepository.deleteByAnalysisId(existing.id)
            analysisJpaRepository.delete(existing)
            // Hibernate는 flush 시 DELETE보다 INSERT를 먼저 실행하므로,
            // (userId, weekOf) 유니크 제약 위반을 피하기 위해 삭제를 즉시 flush한다
            analysisJpaRepository.flush()
        }

        val analysisId = analysis.id ?: UUID.randomUUID()
        analysisJpaRepository.save(
            WeeklyAnalysisJpaEntity(
                id = analysisId,
                userId = analysis.userId,
                weekOf = analysis.weekOf,
                status = analysis.status,
                summary = analysis.summary?.let { objectMapper.writeValueAsString(it) },
            )
        )
        val savedInsights = analysis.insights.map { insight ->
            insightJpaRepository.save(
                InsightJpaEntity(id = insight.id ?: UUID.randomUUID(), analysisId = analysisId, summaryText = insight.summaryText)
            )
        }
        return analysis.copy(id = analysisId, insights = savedInsights.map { it.toDomain() })
    }

    // JPA 엔티티(+insights)를 도메인 모델로 변환
    private fun WeeklyAnalysisJpaEntity.toDomain(insights: List<InsightJpaEntity>) = WeeklyAnalysis(
        id = id, userId = userId, weekOf = weekOf, status = status,
        summary = summary?.let { objectMapper.readValue(it, AnalysisSummary::class.java) },
        insights = insights.map { it.toDomain() },
    )

    // Insight JPA 엔티티를 도메인 모델로 변환
    private fun InsightJpaEntity.toDomain() = Insight(id = id, summaryText = summaryText)
}
