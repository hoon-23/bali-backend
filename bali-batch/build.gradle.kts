plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.3.4"))
    implementation(project(":bali-core"))
    implementation(project(":bali-infra"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Dockerfile에서 build/libs/*.jar로 실행 jar를 특정할 수 있도록 plain jar 생성을 끈다
tasks.named<Jar>("jar") {
    enabled = false
}

// personalArchiveSeed main()이 추가되며 main class가 2개가 되어 bootJar/bootRun이 자동탐지를 못 하므로 명시
springBoot {
    mainClass.set("com.bali.batch.BaliBatchApplicationKt")
}

// 개인 아카이브 실 데이터 1회성 시딩 스크립트 (Airflow/Docker 이미지와 무관, 로컬 실행 전용)
tasks.register<JavaExec>("personalArchiveSeed") {
    group = "application"
    description = "아이폰 메모 아카이브(운동-txt)를 실 계정에 1회성으로 시딩"
    mainClass.set("com.bali.batch.personalarchive.PersonalArchiveSeedMainKt")
    classpath = sourceSets["main"].runtimeClasspath
}
