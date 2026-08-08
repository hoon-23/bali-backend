package com.bali.api.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// Swagger/OpenAPI 문서 설정 - JWT Bearer 인증 스킴을 전역으로 등록
@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI {
        val bearerAuth = "bearerAuth"
        return OpenAPI()
            .info(
                Info()
                    .title("Bali API")
                    .description("운동 기록/분석 서비스 Bali의 REST API 문서")
                    .version("v1")
            )
            .components(
                Components().addSecuritySchemes(
                    bearerAuth,
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                )
            )
            .addSecurityItem(SecurityRequirement().addList(bearerAuth))
    }
}
