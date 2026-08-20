import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    kotlin("jvm") version "2.4.0" apply false
    kotlin("plugin.jpa") version "2.4.0" apply false
    id("org.springframework.boot") version "3.3.4" apply false
    kotlin("plugin.spring") version "2.4.0" apply false
}

allprojects {
    group = "com.bali"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    configure<KotlinJvmProjectExtension> {
        jvmToolchain(21)
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
