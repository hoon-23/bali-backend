package com.bali.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication(scanBasePackages = ["com.bali"])
@EntityScan("com.bali.infra")
@EnableJpaRepositories("com.bali.infra")
class BaliApiApplication

// bali-api 애플리케이션을 시작합니다.
fun main(args: Array<String>) {
    runApplication<BaliApiApplication>(*args)
}
