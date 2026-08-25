package com.bali.batch

import kotlin.system.exitProcess
import org.springframework.beans.factory.getBean
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

// bali-batch Spring Boot 애플리케이션 진입점 (웹 서버 없이 배치 실행 후 종료)
@SpringBootApplication(scanBasePackages = ["com.bali"])
@EntityScan("com.bali.infra")
@EnableJpaRepositories("com.bali.infra")
class BaliBatchApplication

// 첫 번째 non-flag 인자로 실행할 배치 작업을 고르고(기본값 weekly, 기존 Airflow DAG 하위호환),
// 결과 코드를 프로세스 종료 코드로 반영한다 (Airflow가 실패를 감지할 수 있도록)
fun main(args: Array<String>) {
    val context = SpringApplicationBuilder(BaliBatchApplication::class.java)
        .web(WebApplicationType.NONE)
        .run(*args)
    val job = args.firstOrNull { !it.startsWith("--") } ?: "weekly"
    val exitCode = when (job) {
        "weekly" -> context.getBean<WeeklyAnalysisRunner>().run()
        "routine-reminder" -> context.getBean<com.bali.batch.notification.RoutineReminderRunner>().run()
        else -> throw IllegalArgumentException("알 수 없는 배치 작업: $job")
    }
    SpringApplication.exit(context, ExitCodeGenerator { exitCode })
    exitProcess(exitCode)
}
