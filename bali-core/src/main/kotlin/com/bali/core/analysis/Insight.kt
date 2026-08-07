package com.bali.core.analysis

import java.util.UUID

// 규칙 기반으로 생성된 인사이트 문장 하나. WeeklyAnalysis에 종속되므로 자체 참조 필드는 없음
data class Insight(
    val id: UUID?,
    val summaryText: String,
)
