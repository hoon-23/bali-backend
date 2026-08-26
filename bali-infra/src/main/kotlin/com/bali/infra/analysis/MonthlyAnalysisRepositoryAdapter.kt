package com.bali.infra.analysis

import com.bali.core.analysis.AnalysisSummary
import com.bali.core.analysis.Insight
import com.bali.core.analysis.MonthlyAnalysis
import com.bali.core.analysis.MonthlyAnalysisRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

// JPA 백엔드로 MonthlyAnalysisRepository 포트를 구현하는 Spring 리포지토리 어댑터
@Repository
class MonthlyAnalysisRepositoryAdapter(
    private val analysisJpaRepository: MonthlyAnalysisJpaRepository,
    private val insightJpaRepository: MonthlyInsightJpaRepository,
) : MonthlyAnalysisRepository {

    private val objectMapper = jacksonObjectMapper()

    // 특정 유저의 특정 월 분석 결과를 인사이트와 함께 조회
    @Transactional
    override fun findByUserIdAndMonthOf(userId: UUID, monthOf: LocalDate): MonthlyAnalysis? {
        val entity = analysisJpaRepository.findByUserIdAndMonthOf(userId, monthOf) ?: return null
        return entity.toDomain(insightJpaRepository.findByAnalysisId(entity.id))
    }

    // 특정 유저의 전체 분석 결과를 monthOf 내림차순으로, 인사이트와 함께 조회
    @Transactional
    override fun findAllByUserId(userId: UUID): List<MonthlyAnalysis> {
        val entities = analysisJpaRepository.findAllByUserIdOrderByMonthOfDesc(userId)
        val insightsByAnalysisId = insightJpaRepository.findByAnalysisIdIn(entities.map { it.id }).groupBy { it.analysisId }
        return entities.map { it.toDomain(insightsByAnalysisId[it.id] ?: emptyList()) }
    }

    // 분석 결과를 저장 (동일 (userId, monthOf) 기존 레코드가 있으면 삭제 후 재생성)
    @Transactional
    override fun save(analysis: MonthlyAnalysis): MonthlyAnalysis {
        analysisJpaRepository.findByUserIdAndMonthOf(analysis.userId, analysis.monthOf)?.let { existing ->
            insightJpaRepository.deleteByAnalysisId(existing.id)
            analysisJpaRepository.delete(existing)
            // Hibernate는 flush 시 DELETE보다 INSERT를 먼저 실행하므로,
            // (userId, monthOf) 유니크 제약 위반을 피하기 위해 삭제를 즉시 flush한다
            analysisJpaRepository.flush()
        }

        val analysisId = analysis.id ?: UUID.randomUUID()
        analysisJpaRepository.save(
            MonthlyAnalysisJpaEntity(
                id = analysisId,
                userId = analysis.userId,
                monthOf = analysis.monthOf,
                status = analysis.status,
                summary = analysis.summary?.let { objectMapper.writeValueAsString(it) },
            )
        )
        val savedInsights = analysis.insights.map { insight ->
            insightJpaRepository.save(
                MonthlyInsightJpaEntity(id = insight.id ?: UUID.randomUUID(), analysisId = analysisId, summaryText = insight.summaryText)
            )
        }
        return analysis.copy(id = analysisId, insights = savedInsights.map { it.toDomain() })
    }

    // JPA 엔티티(+insights)를 도메인 모델로 변환
    private fun MonthlyAnalysisJpaEntity.toDomain(insights: List<MonthlyInsightJpaEntity>) = MonthlyAnalysis(
        id = id, userId = userId, monthOf = monthOf, status = status,
        summary = summary?.let { objectMapper.readValue(it, AnalysisSummary::class.java) },
        insights = insights.map { it.toDomain() },
    )

    // Insight JPA 엔티티를 도메인 모델로 변환
    private fun MonthlyInsightJpaEntity.toDomain() = Insight(id = id, summaryText = summaryText)
}
