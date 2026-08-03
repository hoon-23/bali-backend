plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "bali_backend"

include("bali-core", "bali-infra", "bali-api")
