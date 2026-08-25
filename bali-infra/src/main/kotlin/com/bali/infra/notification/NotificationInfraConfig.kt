package com.bali.infra.notification

import com.bali.core.notification.NotificationSender
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

// ExpoPushSender는 포트 구현체를 도메인 인터페이스로만 노출하기 위해 @Component 대신 @Bean으로 등록한다
@Configuration
class NotificationInfraConfig {
    @Bean
    fun notificationSender(): NotificationSender = ExpoPushSender(RestClient.create())
}
