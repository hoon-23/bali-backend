package com.bali.api.session

import java.time.LocalDate
import java.util.UUID

// 세션 생성 요청 바디. templateId가 있으면 그 템플릿의 items를 target 스냅샷으로 복사한다
data class SessionCreateRequest(val date: LocalDate, val templateId: UUID?)
