package com.bali.core.template

import java.util.UUID

// 사용자가 정의한 운동 루틴 (Push/Pull/Legs/Strength 등 카테고리별 종목 목록)
data class WorkoutTemplate(
    val id: UUID?,
    val userId: UUID,
    val category: TemplateCategory,
    val name: String,
    val deleted: Boolean = false,
    val items: List<TemplateItem>,
)
