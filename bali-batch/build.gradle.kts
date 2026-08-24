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
