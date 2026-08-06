package com.bali.api.template

import com.bali.core.template.TemplateCategory
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// 템플릿 등록/전체교체(PUT) 요청 바디
data class TemplateCreateRequest(
    val category: TemplateCategory,
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,
    val items: List<TemplateItemRequest>,
)
