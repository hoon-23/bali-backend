# Scaffolding + Core Domain + Auth Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the Gradle multi-module project (`bali-core`, `bali-infra`, `bali-api`), implement the `User` domain model, and get Google OAuth2 login issuing a JWT that protects a real endpoint end-to-end.

**Architecture:** Light hexagonal split across three modules. `bali-core` holds framework-free domain models and repository *port* interfaces. `bali-infra` implements those ports against PostgreSQL/JPA. `bali-api` is the Spring Boot app: security config, OAuth2 login, JWT issuance/validation, and REST controllers. `bali-batch` is intentionally NOT created in this plan — it's added in the batch/Claude plan when it's actually needed (YAGNI).

**Tech Stack:** Kotlin 2.3.10, JVM 21 toolchain, Spring Boot 3.3.4, Spring Security + OAuth2 Client, Spring Data JPA, PostgreSQL, jjwt 0.12.6 for JWT, Kotest (domain unit tests in `bali-core`), JUnit5 + Spring Test + Testcontainers (Spring-context tests in `bali-infra`/`bali-api`).

## Global Constraints

- Group ID for all modules: `com.bali` (replaces the leftover `com.kronos` from the IDE template — this project is unrelated to the Kronos project).
- Root project name stays `bali_backend` (already set in `settings.gradle.kts`).
- JVM toolchain: 21 (per spec).
- DB: PostgreSQL (per spec) — no H2 substitution, even in tests (use Testcontainers).
- Auth: Google OAuth2 login → backend issues its own JWT; `AuthProvider` must be an enum so Kakao etc. can be added later without restructuring (per spec).
- Withdrawal is soft delete: `UserStatus.WITHDRAWN`, never a hard row delete (per spec).
- No admin exercise-normalization feature, no NL-query endpoint — out of scope for this and every Phase 1 plan.
- `bali-batch` module is out of scope for this plan.

---

### Task 1: Root multi-module Gradle scaffolding

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Delete: `src/main/kotlin/Main.kt` (leftover single-module IDE template)

**Interfaces:**
- Produces: three empty Gradle modules — `bali-core`, `bali-infra`, `bali-api` — that later tasks add sources to.

- [ ] **Step 1: Remove the leftover single-module template**

Run: `rm -rf src`

This deletes the IDE-generated `Main.kt` "Hello Kotlin" file, which belonged to the old single-module layout and has no relation to this project's features.

- [ ] **Step 2: Rewrite `settings.gradle.kts`**

```kotlin
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "bali_backend"

include("bali-core", "bali-infra", "bali-api")
```

- [ ] **Step 3: Rewrite root `build.gradle.kts`**

```kotlin
plugins {
    kotlin("jvm") version "2.3.10" apply false
    kotlin("plugin.spring") version "2.3.10" apply false
    kotlin("plugin.jpa") version "2.3.10" apply false
    id("org.springframework.boot") version "3.3.4" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
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

    kotlin {
        jvmToolchain(21)
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
```

- [ ] **Step 4: Create the three empty module directories with minimal build files**

Create `bali-core/build.gradle.kts`:

```kotlin
dependencies {
}
```

Create `bali-infra/build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":bali-core"))
}
```

Create `bali-api/build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":bali-core"))
    implementation(project(":bali-infra"))
}
```

(Each of these gets its real dependency list in the task that adds its first source file — Tasks 2, 4, and 5.)

- [ ] **Step 5: Verify the multi-module build wires up**

Run: `./gradlew projects`
Expected: output lists `Project ':bali-core'`, `Project ':bali-infra'`, `Project ':bali-api'`.

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL` (modules are empty, so this just proves the module graph compiles).

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts build.gradle.kts bali-core/build.gradle.kts bali-infra/build.gradle.kts bali-api/build.gradle.kts
git rm -r src
git commit -m "build: convert to multi-module Gradle project (core/infra/api)"
```

---

### Task 2: `bali-core` — User domain model

**Files:**
- Modify: `bali-core/build.gradle.kts`
- Create: `bali-core/src/main/kotlin/com/bali/core/user/AuthProvider.kt`
- Create: `bali-core/src/main/kotlin/com/bali/core/user/UserStatus.kt`
- Create: `bali-core/src/main/kotlin/com/bali/core/user/User.kt`
- Create: `bali-core/src/main/kotlin/com/bali/core/user/UserRepository.kt`
- Test: `bali-core/src/test/kotlin/com/bali/core/user/UserTest.kt`

**Interfaces:**
- Produces: `User(id: UUID?, email: String, provider: AuthProvider, providerId: String, status: UserStatus, createdAt: Instant)` with method `withdraw(): User`.
- Produces: `UserRepository` port with `findById(id: UUID): User?`, `findByProviderAndProviderId(provider: AuthProvider, providerId: String): User?`, `save(user: User): User`.
- Consumed by: Task 4 (`bali-infra` implements `UserRepository`), Task 7/9 (`bali-api` calls it).

- [ ] **Step 1: Add Kotest to `bali-core`**

Update `bali-core/build.gradle.kts`:

```kotlin
dependencies {
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
}
```

- [ ] **Step 2: Write the failing test**

Create `bali-core/src/test/kotlin/com/bali/core/user/UserTest.kt`:

```kotlin
package com.bali.core.user

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

class UserTest : StringSpec({

    fun newUser() = User(
        id = UUID.randomUUID(),
        email = "test@example.com",
        provider = AuthProvider.GOOGLE,
        providerId = "google-sub-123",
        status = UserStatus.ACTIVE,
        createdAt = Instant.now(),
    )

    "a newly constructed user is ACTIVE by default in tests" {
        newUser().status shouldBe UserStatus.ACTIVE
    }

    "withdraw() transitions status to WITHDRAWN" {
        val withdrawn = newUser().withdraw()
        withdrawn.status shouldBe UserStatus.WITHDRAWN
    }

    "withdraw() is idempotent" {
        val withdrawn = newUser().withdraw().withdraw()
        withdrawn.status shouldBe UserStatus.WITHDRAWN
    }
})
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :bali-core:test --tests "com.bali.core.user.UserTest"`
Expected: FAIL to compile — `User`, `AuthProvider`, `UserStatus` are unresolved references.

- [ ] **Step 4: Write the minimal implementation**

Create `bali-core/src/main/kotlin/com/bali/core/user/AuthProvider.kt`:

```kotlin
package com.bali.core.user

enum class AuthProvider {
    GOOGLE,
}
```

Create `bali-core/src/main/kotlin/com/bali/core/user/UserStatus.kt`:

```kotlin
package com.bali.core.user

enum class UserStatus {
    ACTIVE,
    WITHDRAWN,
}
```

Create `bali-core/src/main/kotlin/com/bali/core/user/User.kt`:

```kotlin
package com.bali.core.user

import java.time.Instant
import java.util.UUID

data class User(
    val id: UUID?,
    val email: String,
    val provider: AuthProvider,
    val providerId: String,
    val status: UserStatus,
    val createdAt: Instant,
) {
    fun withdraw(): User = copy(status = UserStatus.WITHDRAWN)
}
```

Create `bali-core/src/main/kotlin/com/bali/core/user/UserRepository.kt`:

```kotlin
package com.bali.core.user

import java.util.UUID

interface UserRepository {
    fun findById(id: UUID): User?
    fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): User?
    fun save(user: User): User
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :bali-core:test --tests "com.bali.core.user.UserTest"`
Expected: `BUILD SUCCESSFUL`, 3 tests passed.

- [ ] **Step 6: Commit**

```bash
git add bali-core
git commit -m "feat(core): add User domain model and UserRepository port"
```

---

### Task 3: PostgreSQL local infra (docker-compose)

**Files:**
- Create: `docker-compose.yml`

**Interfaces:**
- Produces: a local Postgres instance on `localhost:5432`, db `bali`, user/password `bali`/`bali`, consumed by Task 5's `application.yml`.

- [ ] **Step 1: Create `docker-compose.yml`**

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: bali
      POSTGRES_USER: bali
      POSTGRES_PASSWORD: bali
    ports:
      - "5432:5432"
    volumes:
      - bali_postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U bali"]
      interval: 5s
      timeout: 3s
      retries: 5

volumes:
  bali_postgres_data:
```

- [ ] **Step 2: Verify it starts and is healthy**

Run: `docker compose up -d`
Run: `docker compose ps`
Expected: `postgres` service shows `healthy`.

- [ ] **Step 3: Commit**

```bash
git add docker-compose.yml
git commit -m "chore: add docker-compose for local PostgreSQL"
```

---

### Task 4: `bali-infra` — JPA adapter for `UserRepository`

**Files:**
- Modify: `bali-infra/build.gradle.kts`
- Create: `bali-infra/src/main/kotlin/com/bali/infra/user/UserJpaEntity.kt`
- Create: `bali-infra/src/main/kotlin/com/bali/infra/user/UserJpaRepository.kt`
- Create: `bali-infra/src/main/kotlin/com/bali/infra/user/UserRepositoryAdapter.kt`
- Test: `bali-infra/src/test/kotlin/com/bali/infra/InfraTestConfig.kt`
- Test: `bali-infra/src/test/kotlin/com/bali/infra/user/UserRepositoryAdapterTest.kt`

**Interfaces:**
- Consumes: `com.bali.core.user.User`, `AuthProvider`, `UserStatus`, `UserRepository` (Task 2).
- Produces: `UserRepositoryAdapter : UserRepository` — a Spring `@Repository` bean, picked up by `bali-api`'s component scan in Task 5.

- [ ] **Step 1: Add JPA + Postgres + Testcontainers dependencies**

Update `bali-infra/build.gradle.kts`:

```kotlin
plugins {
    kotlin("plugin.jpa")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.4")
    }
}

dependencies {
    implementation(project(":bali-core"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:postgresql:1.20.1")
    testImplementation("org.testcontainers:junit-jupiter:1.20.1")
}
```

- [ ] **Step 2: Write the failing test**

Create `bali-infra/src/test/kotlin/com/bali/infra/InfraTestConfig.kt` (lets `@DataJpaTest` find entities/repositories without a real `@SpringBootApplication` class):

```kotlin
package com.bali.infra

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan("com.bali.infra")
@EnableJpaRepositories("com.bali.infra")
class InfraTestConfig
```

Create `bali-infra/src/test/kotlin/com/bali/infra/user/UserRepositoryAdapterTest.kt`:

```kotlin
package com.bali.infra.user

import com.bali.core.user.AuthProvider
import com.bali.core.user.User
import com.bali.core.user.UserStatus
import com.bali.infra.InfraTestConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = [InfraTestConfig::class])
@Import(UserRepositoryAdapter::class)
@Testcontainers
class UserRepositoryAdapterTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16")

        @DynamicPropertySource
        @JvmStatic
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.jpa.hibernate.ddl-auto") { "create-drop" }
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    lateinit var adapter: UserRepositoryAdapter

    @Test
    fun `save then findByProviderAndProviderId returns the same user`() {
        val saved = adapter.save(
            User(
                id = null,
                email = "test@example.com",
                provider = AuthProvider.GOOGLE,
                providerId = "google-sub-123",
                status = UserStatus.ACTIVE,
                createdAt = Instant.now(),
            )
        )

        val found = adapter.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-123")

        assertEquals(saved.id, found?.id)
        assertEquals("test@example.com", found?.email)
    }

    @Test
    fun `findByProviderAndProviderId returns null when no match`() {
        val found = adapter.findByProviderAndProviderId(AuthProvider.GOOGLE, "does-not-exist")
        assertNull(found)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :bali-infra:test --tests "com.bali.infra.user.UserRepositoryAdapterTest"`
Expected: FAIL to compile — `UserJpaEntity`, `UserJpaRepository`, `UserRepositoryAdapter` are unresolved references.

- [ ] **Step 4: Write the minimal implementation**

Create `bali-infra/src/main/kotlin/com/bali/infra/user/UserJpaEntity.kt`:

```kotlin
package com.bali.infra.user

import com.bali.core.user.AuthProvider
import com.bali.core.user.UserStatus
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "users",
    uniqueConstraints = [UniqueConstraint(columnNames = ["provider", "provider_id"])],
)
class UserJpaEntity(
    @Id
    var id: UUID = UUID.randomUUID(),

    var email: String = "",

    @Enumerated(EnumType.STRING)
    var provider: AuthProvider = AuthProvider.GOOGLE,

    var providerId: String = "",

    @Enumerated(EnumType.STRING)
    var status: UserStatus = UserStatus.ACTIVE,

    var createdAt: Instant = Instant.now(),
)
```

Create `bali-infra/src/main/kotlin/com/bali/infra/user/UserJpaRepository.kt`:

```kotlin
package com.bali.infra.user

import com.bali.core.user.AuthProvider
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserJpaRepository : JpaRepository<UserJpaEntity, UUID> {
    fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): UserJpaEntity?
}
```

Create `bali-infra/src/main/kotlin/com/bali/infra/user/UserRepositoryAdapter.kt`:

```kotlin
package com.bali.infra.user

import com.bali.core.user.AuthProvider
import com.bali.core.user.User
import com.bali.core.user.UserRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class UserRepositoryAdapter(
    private val jpaRepository: UserJpaRepository,
) : UserRepository {

    override fun findById(id: UUID): User? =
        jpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): User? =
        jpaRepository.findByProviderAndProviderId(provider, providerId)?.toDomain()

    override fun save(user: User): User {
        val entity = UserJpaEntity(
            id = user.id ?: UUID.randomUUID(),
            email = user.email,
            provider = user.provider,
            providerId = user.providerId,
            status = user.status,
            createdAt = user.createdAt,
        )
        return jpaRepository.save(entity).toDomain()
    }

    private fun UserJpaEntity.toDomain() = User(
        id = id,
        email = email,
        provider = provider,
        providerId = providerId,
        status = status,
        createdAt = createdAt,
    )
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :bali-infra:test --tests "com.bali.infra.user.UserRepositoryAdapterTest"`
Expected: `BUILD SUCCESSFUL`, 2 tests passed. (Requires Docker running locally for Testcontainers.)

- [ ] **Step 6: Commit**

```bash
git add bali-infra
git commit -m "feat(infra): add JPA adapter for UserRepository"
```

---

### Task 5: `bali-api` — Spring Boot app skeleton + health check

**Files:**
- Modify: `bali-api/build.gradle.kts`
- Create: `bali-api/src/main/kotlin/com/bali/api/BaliApiApplication.kt`
- Create: `bali-api/src/main/resources/application.yml`
- Test: `bali-api/src/test/kotlin/com/bali/api/BaliApiApplicationTest.kt`

**Interfaces:**
- Produces: a running Spring Boot context on `com.bali` base package (picks up `bali-core` and `bali-infra` beans), reachable at `http://localhost:8080`.

- [ ] **Step 1: Add Spring Boot web + actuator + JPA runtime deps**

Update `bali-api/build.gradle.kts`:

```kotlin
plugins {
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.4")
    }
}

dependencies {
    implementation(project(":bali-core"))
    implementation(project(":bali-infra"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
```

- [ ] **Step 2: Write the failing test**

Create `bali-api/src/test/kotlin/com/bali/api/BaliApiApplicationTest.kt`:

```kotlin
package com.bali.api

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.junit.jupiter.api.Assertions.assertEquals

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BaliApiApplicationTest {

    @LocalServerPort
    var port: Int = 0

    private val restTemplate = TestRestTemplate()

    @Test
    fun `actuator health endpoint responds UP`() {
        val response = restTemplate.getForEntity(
            "http://localhost:$port/actuator/health",
            String::class.java,
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assert(response.body?.contains("UP") == true)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :bali-api:test --tests "com.bali.api.BaliApiApplicationTest"`
Expected: FAIL — no `@SpringBootApplication` class exists yet, context fails to load.

- [ ] **Step 4: Write the minimal implementation**

Create `bali-api/src/main/kotlin/com/bali/api/BaliApiApplication.kt`:

```kotlin
package com.bali.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication(scanBasePackages = ["com.bali"])
@EntityScan("com.bali.infra")
@EnableJpaRepositories("com.bali.infra")
class BaliApiApplication

fun main(args: Array<String>) {
    runApplication<BaliApiApplication>(*args)
}
```

Create `bali-api/src/main/resources/application.yml`:

```yaml
server:
  port: 8080

spring:
  application:
    name: bali-api
  datasource:
    url: jdbc:postgresql://localhost:5432/bali
    username: bali
    password: bali
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false

management:
  endpoints:
    web:
      exposure:
        include: health
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :bali-api:test --tests "com.bali.api.BaliApiApplicationTest"`
Expected: `BUILD SUCCESSFUL`. (Requires `docker compose up -d` from Task 3 running, since `ddl-auto: update` connects to real Postgres on startup.)

- [ ] **Step 6: Commit**

```bash
git add bali-api
git commit -m "feat(api): add Spring Boot application skeleton with health check"
```

---

### Task 6: JWT token provider

**Files:**
- Modify: `bali-api/build.gradle.kts`
- Create: `bali-api/src/main/kotlin/com/bali/api/auth/JwtTokenProvider.kt`
- Modify: `bali-api/src/main/resources/application.yml`
- Test: `bali-api/src/test/kotlin/com/bali/api/auth/JwtTokenProviderTest.kt`

**Interfaces:**
- Produces: `JwtTokenProvider.generateToken(userId: UUID, email: String): String` and `JwtTokenProvider.validateAndGetUserId(token: String): UUID?` (returns `null` on any invalid/expired/malformed token).
- Consumed by: Task 7 (issues tokens on login), Task 8 (validates tokens on incoming requests).

- [ ] **Step 1: Add jjwt dependencies**

Update `bali-api/build.gradle.kts` dependencies block, adding:

```kotlin
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
```

- [ ] **Step 2: Write the failing test**

Create `bali-api/src/test/kotlin/com/bali/api/auth/JwtTokenProviderTest.kt`:

```kotlin
package com.bali.api.auth

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import java.util.UUID

class JwtTokenProviderTest {

    private val provider = JwtTokenProvider(
        secret = "test-secret-key-must-be-at-least-32-bytes-long!!",
        expirationMillis = 3600_000,
    )

    @Test
    fun `token generated for a user validates back to the same user id`() {
        val userId = UUID.randomUUID()
        val token = provider.generateToken(userId, "test@example.com")

        assertEquals(userId, provider.validateAndGetUserId(token))
    }

    @Test
    fun `garbage token returns null instead of throwing`() {
        assertNull(provider.validateAndGetUserId("not-a-real-token"))
    }

    @Test
    fun `token signed with a different secret returns null`() {
        val userId = UUID.randomUUID()
        val token = provider.generateToken(userId, "test@example.com")

        val otherProvider = JwtTokenProvider(
            secret = "a-completely-different-secret-key-32-bytes!!",
            expirationMillis = 3600_000,
        )

        assertNull(otherProvider.validateAndGetUserId(token))
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :bali-api:test --tests "com.bali.api.auth.JwtTokenProviderTest"`
Expected: FAIL to compile — `JwtTokenProvider` is an unresolved reference.

- [ ] **Step 4: Write the minimal implementation**

Create `bali-api/src/main/kotlin/com/bali/api/auth/JwtTokenProvider.kt`:

```kotlin
package com.bali.api.auth

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    @Value("\${bali.jwt.secret}") private val secret: String,
    @Value("\${bali.jwt.expiration-millis}") private val expirationMillis: Long,
) {
    private val key: SecretKey by lazy { Keys.hmacShaKeyFor(secret.toByteArray()) }

    fun generateToken(userId: UUID, email: String): String {
        val now = Date()
        val expiry = Date(now.time + expirationMillis)

        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }

    fun validateAndGetUserId(token: String): UUID? = try {
        val claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload

        UUID.fromString(claims.subject)
    } catch (ex: Exception) {
        null
    }
}
```

Update `bali-api/src/main/resources/application.yml`, adding under a new top-level `bali:` key:

```yaml
bali:
  jwt:
    secret: ${JWT_SECRET:dev-only-secret-change-me-in-prod-32bytes!!}
    expiration-millis: 3600000
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :bali-api:test --tests "com.bali.api.auth.JwtTokenProviderTest"`
Expected: `BUILD SUCCESSFUL`, 3 tests passed.

- [ ] **Step 6: Commit**

```bash
git add bali-api
git commit -m "feat(api): add JWT token provider"
```

---

### Task 7: Google OAuth2 login → JWT issuance

**Files:**
- Modify: `bali-api/build.gradle.kts`
- Modify: `bali-api/src/main/resources/application.yml`
- Create: `bali-api/src/main/kotlin/com/bali/api/auth/CustomOAuth2UserService.kt`
- Create: `bali-api/src/main/kotlin/com/bali/api/auth/OAuth2LoginSuccessHandler.kt`
- Create: `bali-api/src/main/kotlin/com/bali/api/config/SecurityConfig.kt`
- Test: `bali-api/src/test/kotlin/com/bali/api/auth/CustomOAuth2UserServiceTest.kt`
- Test: `bali-api/src/test/kotlin/com/bali/api/auth/OAuth2LoginSuccessHandlerTest.kt`

**Interfaces:**
- Consumes: `UserRepository` (Task 2/4), `JwtTokenProvider` (Task 6).
- Produces: on successful Google login, an HTTP response `{"accessToken": "<jwt>"}`.

A real end-to-end browser OAuth flow can't run in an automated test (it needs a live Google consent screen). This task tests the two pieces of *our* logic in isolation — user lookup/creation, and JWT response writing — and documents a manual browser check for the full flow.

- [ ] **Step 1: Add OAuth2 client + security dependencies**

Update `bali-api/build.gradle.kts` dependencies block, adding:

```kotlin
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-security")

    testImplementation("org.springframework.security:spring-security-test")
```

- [ ] **Step 2: Write the failing test for user lookup/creation**

Create `bali-api/src/test/kotlin/com/bali/api/auth/CustomOAuth2UserServiceTest.kt`:

```kotlin
package com.bali.api.auth

import com.bali.core.user.AuthProvider
import com.bali.core.user.User
import com.bali.core.user.UserRepository
import com.bali.core.user.UserStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class CustomOAuth2UserServiceTest {

    @Test
    fun `resolveUser creates a new user on first login`() {
        val repository = InMemoryUserRepository()
        val service = CustomOAuth2UserService(repository)

        val user = service.resolveUser(providerId = "google-sub-1", email = "new@example.com")

        assertEquals("new@example.com", user.email)
        assertEquals(AuthProvider.GOOGLE, user.provider)
        assertEquals(UserStatus.ACTIVE, user.status)
    }

    @Test
    fun `resolveUser returns the existing user on repeat login`() {
        val repository = InMemoryUserRepository()
        val service = CustomOAuth2UserService(repository)

        val first = service.resolveUser(providerId = "google-sub-2", email = "again@example.com")
        val second = service.resolveUser(providerId = "google-sub-2", email = "again@example.com")

        assertEquals(first.id, second.id)
    }

    private class InMemoryUserRepository : UserRepository {
        private val store = mutableMapOf<UUID, User>()

        override fun findById(id: UUID): User? = store[id]

        override fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): User? =
            store.values.find { it.provider == provider && it.providerId == providerId }

        override fun save(user: User): User {
            val toSave = user.copy(id = user.id ?: UUID.randomUUID())
            store[toSave.id!!] = toSave
            return toSave
        }
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :bali-api:test --tests "com.bali.api.auth.CustomOAuth2UserServiceTest"`
Expected: FAIL to compile — `CustomOAuth2UserService` is an unresolved reference.

- [ ] **Step 4: Write `CustomOAuth2UserService`**

Create `bali-api/src/main/kotlin/com/bali/api/auth/CustomOAuth2UserService.kt`:

```kotlin
package com.bali.api.auth

import com.bali.core.user.AuthProvider
import com.bali.core.user.User
import com.bali.core.user.UserRepository
import com.bali.core.user.UserStatus
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class CustomOAuth2UserService(
    private val userRepository: UserRepository,
) : DefaultOAuth2UserService() {

    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val oAuth2User = super.loadUser(userRequest)
        val providerId = oAuth2User.getAttribute<String>("sub")!!
        val email = oAuth2User.getAttribute<String>("email")!!

        resolveUser(providerId, email)

        return oAuth2User
    }

    fun resolveUser(providerId: String, email: String): User {
        val existing = userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, providerId)
        if (existing != null) return existing

        return userRepository.save(
            User(
                id = null,
                email = email,
                provider = AuthProvider.GOOGLE,
                providerId = providerId,
                status = UserStatus.ACTIVE,
                createdAt = Instant.now(),
            )
        )
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :bali-api:test --tests "com.bali.api.auth.CustomOAuth2UserServiceTest"`
Expected: `BUILD SUCCESSFUL`, 2 tests passed.

- [ ] **Step 6: Write the failing test for JWT response writing**

Create `bali-api/src/test/kotlin/com/bali/api/auth/OAuth2LoginSuccessHandlerTest.kt`:

```kotlin
package com.bali.api.auth

import com.bali.core.user.AuthProvider
import com.bali.core.user.User
import com.bali.core.user.UserRepository
import com.bali.core.user.UserStatus
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import java.time.Instant
import java.util.UUID

class OAuth2LoginSuccessHandlerTest {

    @Test
    fun `writes a JSON access token for the resolved user`() {
        val userId = UUID.randomUUID()
        val user = User(
            id = userId,
            email = "test@example.com",
            provider = AuthProvider.GOOGLE,
            providerId = "google-sub-1",
            status = UserStatus.ACTIVE,
            createdAt = Instant.now(),
        )

        val userRepository = object : UserRepository {
            override fun findById(id: UUID) = null
            override fun findByProviderAndProviderId(provider: AuthProvider, providerId: String) = user
            override fun save(user: User) = user
        }

        val jwtTokenProvider = JwtTokenProvider(
            secret = "test-secret-key-must-be-at-least-32-bytes-long!!",
            expirationMillis = 3600_000,
        )

        val handler = OAuth2LoginSuccessHandler(userRepository, jwtTokenProvider)

        val oAuth2User: OAuth2User = DefaultOAuth2User(
            emptyList(),
            mapOf("sub" to "google-sub-1", "email" to "test@example.com"),
            "sub",
        )
        val authentication = TestingAuthenticationToken(oAuth2User, null)

        val response = MockHttpServletResponse()
        handler.onAuthenticationSuccess(
            org.springframework.mock.web.MockHttpServletRequest() as HttpServletRequest,
            response,
            authentication,
        )

        assertEquals(200, response.status)
        assertTrue(response.contentAsString.contains("accessToken"))
    }
}
```

- [ ] **Step 7: Run test to verify it fails**

Run: `./gradlew :bali-api:test --tests "com.bali.api.auth.OAuth2LoginSuccessHandlerTest"`
Expected: FAIL to compile — `OAuth2LoginSuccessHandler` is an unresolved reference.

- [ ] **Step 8: Write `OAuth2LoginSuccessHandler`**

Create `bali-api/src/main/kotlin/com/bali/api/auth/OAuth2LoginSuccessHandler.kt`:

```kotlin
package com.bali.api.auth

import com.bali.core.user.AuthProvider
import com.bali.core.user.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class OAuth2LoginSuccessHandler(
    private val userRepository: UserRepository,
    private val jwtTokenProvider: JwtTokenProvider,
) : AuthenticationSuccessHandler {

    private val objectMapper = ObjectMapper()

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val oAuth2User = authentication.principal as OAuth2User
        val providerId = oAuth2User.getAttribute<String>("sub")!!

        val user = userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, providerId)
            ?: error("User must already be resolved by CustomOAuth2UserService before success handler runs")

        val token = jwtTokenProvider.generateToken(user.id!!, user.email)

        response.status = HttpServletResponse.SC_OK
        response.contentType = "application/json"
        response.writer.write(objectMapper.writeValueAsString(mapOf("accessToken" to token)))
    }
}
```

- [ ] **Step 9: Run test to verify it passes**

Run: `./gradlew :bali-api:test --tests "com.bali.api.auth.OAuth2LoginSuccessHandlerTest"`
Expected: `BUILD SUCCESSFUL`, 1 test passed.

- [ ] **Step 10: Wire it into Spring Security config**

Create `bali-api/src/main/kotlin/com/bali/api/config/SecurityConfig.kt`:

```kotlin
package com.bali.api.config

import com.bali.api.auth.CustomOAuth2UserService
import com.bali.api.auth.OAuth2LoginSuccessHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig(
    private val customOAuth2UserService: CustomOAuth2UserService,
    private val oAuth2LoginSuccessHandler: OAuth2LoginSuccessHandler,
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            csrf { disable() }
            sessionManagement { sessionCreationPolicy = org.springframework.security.config.http.SessionCreationPolicy.STATELESS }
            authorizeHttpRequests {
                authorize("/actuator/health", permitAll)
                authorize("/oauth2/**", permitAll)
                authorize("/login/oauth2/**", permitAll)
                authorize(anyRequest, authenticated)
            }
            oauth2Login {
                userInfoEndpoint {
                    userService = customOAuth2UserService
                }
                authenticationSuccessHandler = oAuth2LoginSuccessHandler
            }
        }
        return http.build()
    }
}
```

Login is Spring Security's standard OAuth2 redirect flow, not a custom controller: the client navigates to `/oauth2/authorization/google` (Spring Security serves this automatically once `oauth2Login` is configured), Google redirects back to `/login/oauth2/code/google` (also automatic), and `OAuth2LoginSuccessHandler` writes the JWT JSON response. No `/api/v1/auth/**` route exists in this plan.

Update `bali-api/src/main/resources/application.yml`, adding:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: openid, email, profile
```

- [ ] **Step 11: Run the full `bali-api` test suite**

Run: `./gradlew :bali-api:test`
Expected: `BUILD SUCCESSFUL`, all tests pass (health check, JWT provider, OAuth2 user service, success handler).

- [ ] **Step 12: Manual verification (documented, not automated)**

This step cannot be automated — record it as a manual QA checklist item for whoever has real Google OAuth credentials:
1. Register a Google OAuth2 client ID/secret for `http://localhost:8080/login/oauth2/code/google` as the redirect URI.
2. Export `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` env vars, run `./gradlew :bali-api:bootRun`.
3. Visit `http://localhost:8080/oauth2/authorization/google` in a browser, complete Google login.
4. Confirm the response body is `{"accessToken": "<a JWT string>"}`.

- [ ] **Step 13: Commit**

```bash
git add bali-api
git commit -m "feat(api): add Google OAuth2 login issuing JWT access tokens"
```

---

### Task 8: JWT authentication filter for protected endpoints

**Files:**
- Create: `bali-api/src/main/kotlin/com/bali/api/auth/JwtAuthenticationFilter.kt`
- Modify: `bali-api/src/main/kotlin/com/bali/api/config/SecurityConfig.kt`
- Create: `bali-api/src/test/kotlin/com/bali/api/auth/JwtAuthenticationFilterTest.kt`

**Interfaces:**
- Consumes: `JwtTokenProvider.validateAndGetUserId` (Task 6).
- Produces: on a valid `Authorization: Bearer <jwt>` header, sets `SecurityContextHolder` authentication with the user's UUID as principal name, so `@AuthenticationPrincipal`/`SecurityContextHolder` in controllers (Task 9) can read the current user id.

- [ ] **Step 1: Write the failing test**

Create `bali-api/src/test/kotlin/com/bali/api/auth/JwtAuthenticationFilterTest.kt`:

```kotlin
package com.bali.api.auth

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

class JwtAuthenticationFilterTest {

    private val jwtTokenProvider = JwtTokenProvider(
        secret = "test-secret-key-must-be-at-least-32-bytes-long!!",
        expirationMillis = 3600_000,
    )
    private val filter = JwtAuthenticationFilter(jwtTokenProvider)

    @Test
    fun `valid bearer token sets the authenticated user id in the security context`() {
        SecurityContextHolder.clearContext()
        val userId = UUID.randomUUID()
        val token = jwtTokenProvider.generateToken(userId, "test@example.com")

        val request = MockHttpServletRequest()
        request.addHeader("Authorization", "Bearer $token")
        val response = MockHttpServletResponse()
        val chain = FilterChain { _, _ -> }

        filter.doFilter(request, response, chain)

        assertEquals(userId.toString(), SecurityContextHolder.getContext().authentication?.name)
    }

    @Test
    fun `missing header leaves security context empty`() {
        SecurityContextHolder.clearContext()
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        val chain = FilterChain { _, _ -> }

        filter.doFilter(request, response, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `invalid token leaves security context empty`() {
        SecurityContextHolder.clearContext()
        val request = MockHttpServletRequest()
        request.addHeader("Authorization", "Bearer garbage")
        val response = MockHttpServletResponse()
        val chain = FilterChain { _, _ -> }

        filter.doFilter(request, response, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :bali-api:test --tests "com.bali.api.auth.JwtAuthenticationFilterTest"`
Expected: FAIL to compile — `JwtAuthenticationFilter` is an unresolved reference.

- [ ] **Step 3: Write `JwtAuthenticationFilter`**

Create `bali-api/src/main/kotlin/com/bali/api/auth/JwtAuthenticationFilter.kt`:

```kotlin
package com.bali.api.auth

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ")) {
            val token = header.removePrefix("Bearer ")
            val userId = jwtTokenProvider.validateAndGetUserId(token)
            if (userId != null) {
                val authentication = UsernamePasswordAuthenticationToken(userId.toString(), null, emptyList())
                SecurityContextHolder.getContext().authentication = authentication
            }
        }
        filterChain.doFilter(request, response)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :bali-api:test --tests "com.bali.api.auth.JwtAuthenticationFilterTest"`
Expected: `BUILD SUCCESSFUL`, 3 tests passed.

- [ ] **Step 5: Register the filter in `SecurityConfig`**

Modify `bali-api/src/main/kotlin/com/bali/api/config/SecurityConfig.kt`: add `jwtAuthenticationFilter: JwtAuthenticationFilter` as a constructor parameter, and inside `securityFilterChain`, add before the `oauth2Login` block:

```kotlin
            addFilterBefore<org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter>(jwtAuthenticationFilter)
```

`/actuator/health`, `/oauth2/**`, and `/login/oauth2/**` stay `permitAll` (set in Task 7 Step 10); everything else — including `/api/v1/**` — falls through to `authenticated`. This is the exact rule Task 9's endpoints rely on.

- [ ] **Step 6: Run the full `bali-api` test suite**

Run: `./gradlew :bali-api:test`
Expected: `BUILD SUCCESSFUL`, all tests still pass.

- [ ] **Step 7: Commit**

```bash
git add bali-api
git commit -m "feat(api): add JWT authentication filter for protected endpoints"
```

---

### Task 9: `GET /api/v1/users/me` and `DELETE /api/v1/users/me`

**Files:**
- Create: `bali-api/src/main/kotlin/com/bali/api/user/UserController.kt`
- Create: `bali-api/src/main/kotlin/com/bali/api/user/UserResponse.kt`
- Test: `bali-api/src/test/kotlin/com/bali/api/user/UserControllerTest.kt`

**Interfaces:**
- Consumes: `UserRepository` (Task 2/4), `SecurityContextHolder` authentication name as user id (Task 8).
- Produces: `GET /api/v1/users/me` → `200 {id, email, status}` or `401` without a valid token; `DELETE /api/v1/users/me` → `204`, sets `UserStatus.WITHDRAWN`.

- [ ] **Step 1: Write the failing test**

Create `bali-api/src/test/kotlin/com/bali/api/user/UserControllerTest.kt`:

```kotlin
package com.bali.api.user

import com.bali.api.auth.JwtTokenProvider
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jwtTokenProvider: JwtTokenProvider

    @Autowired
    lateinit var userJpaRepository: com.bali.infra.user.UserJpaRepository

    @Test
    fun `GET me without a token returns 401`() {
        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `GET me with a valid token returns the user`() {
        val entity = userJpaRepository.save(
            com.bali.infra.user.UserJpaEntity(
                email = "me@example.com",
                provider = com.bali.core.user.AuthProvider.GOOGLE,
                providerId = "sub-me",
            )
        )
        val token = jwtTokenProvider.generateToken(entity.id, entity.email)

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
    }

    @Test
    fun `DELETE me sets status to WITHDRAWN`() {
        val entity = userJpaRepository.save(
            com.bali.infra.user.UserJpaEntity(
                email = "withdraw@example.com",
                provider = com.bali.core.user.AuthProvider.GOOGLE,
                providerId = "sub-withdraw",
            )
        )
        val token = jwtTokenProvider.generateToken(entity.id, entity.email)

        mockMvc.perform(delete("/api/v1/users/me").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)

        val reloaded = userJpaRepository.findById(entity.id).orElseThrow()
        org.junit.jupiter.api.Assertions.assertEquals(
            com.bali.core.user.UserStatus.WITHDRAWN,
            reloaded.status,
        )
    }
}
```

This test requires a running Postgres (`docker compose up -d`) since `@SpringBootTest` here boots the full app context against `application.yml`'s real datasource — consistent with how Task 5/7 tests already run.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :bali-api:test --tests "com.bali.api.user.UserControllerTest"`
Expected: `GET me without a token` passes already (falls through to `authenticated()` → 401 by default Spring Security behavior), but the other two FAIL — `404 Not Found`, since no controller exists yet for `/api/v1/users/me`.

- [ ] **Step 3: Write the minimal implementation**

Create `bali-api/src/main/kotlin/com/bali/api/user/UserResponse.kt`:

```kotlin
package com.bali.api.user

import com.bali.core.user.User
import com.bali.core.user.UserStatus
import java.util.UUID

data class UserResponse(
    val id: UUID,
    val email: String,
    val status: UserStatus,
) {
    companion object {
        fun from(user: User) = UserResponse(id = user.id!!, email = user.email, status = user.status)
    }
}
```

Create `bali-api/src/main/kotlin/com/bali/api/user/UserController.kt`:

```kotlin
package com.bali.api.user

import com.bali.core.user.UserRepository
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userRepository: UserRepository,
) {

    @GetMapping("/me")
    fun getMe(): ResponseEntity<UserResponse> {
        val user = userRepository.findById(currentUserId())
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(UserResponse.from(user))
    }

    @DeleteMapping("/me")
    fun withdraw(): ResponseEntity<Void> {
        val user = userRepository.findById(currentUserId())
            ?: return ResponseEntity.notFound().build()
        userRepository.save(user.withdraw())
        return ResponseEntity.noContent().build()
    }

    private fun currentUserId(): UUID =
        UUID.fromString(SecurityContextHolder.getContext().authentication.name)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :bali-api:test --tests "com.bali.api.user.UserControllerTest"`
Expected: `BUILD SUCCESSFUL`, 3 tests passed.

- [ ] **Step 5: Commit**

```bash
git add bali-api
git commit -m "feat(api): add GET/DELETE /api/v1/users/me endpoints"
```

---

### Task 10: GitHub Actions CI

**Files:**
- Create: `.github/workflows/ci.yml`

**Interfaces:**
- Produces: a CI job that runs `./gradlew build` (including all tests from Tasks 2–9) against a real Postgres service container on every push/PR to `main` or `develop`.

- [ ] **Step 1: Create the workflow**

```yaml
name: CI

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main, develop]

jobs:
  build:
    runs-on: ubuntu-latest

    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_DB: bali
          POSTGRES_USER: bali
          POSTGRES_PASSWORD: bali
        ports:
          - 5432:5432
        options: >-
          --health-cmd "pg_isready -U bali"
          --health-interval 5s
          --health-timeout 3s
          --health-retries 5

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Build and test
        env:
          GOOGLE_CLIENT_ID: dummy-client-id
          GOOGLE_CLIENT_SECRET: dummy-client-secret
        run: ./gradlew build
```

- [ ] **Step 2: Verify locally that the referenced command succeeds**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL` (requires local `docker compose up -d` from Task 3, and `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` env vars set to any non-empty value so Spring Boot's OAuth2 client auto-configuration doesn't fail context startup).

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add GitHub Actions workflow for build and test"
```

---

## Self-Review Notes

- **Spec coverage:** Covers spec sections "인증 흐름" (Google OAuth2 → JWT, Tasks 6–8) and the `User` part of "도메인 모델" (Task 2), plus the `bali-core`/`bali-infra`/`bali-api` slice of "모듈 구조" (Task 1). Exercise/template/session/analysis domain, batch, and the remaining API endpoints are deliberately out of scope — they belong to Plans 2–5 per `PROGRESS.md`.
- **Spec correction applied:** the spec's original `POST /api/v1/auth/google/login` line described the login endpoint at the wrong abstraction level. This plan uses Spring Security's standard OAuth2 login redirect flow (`/oauth2/authorization/google` to start, automatic `/login/oauth2/code/google` callback) instead of a custom controller — simpler and no less functional. The spec doc has been updated to match.
- **Type consistency checked:** `UserRepository` signature (Task 2) is used identically in `UserRepositoryAdapter` (Task 4), `CustomOAuth2UserService` (Task 7), `OAuth2LoginSuccessHandler` (Task 7), and `UserController` (Task 9). `JwtTokenProvider.generateToken(userId: UUID, email: String)` / `validateAndGetUserId(token: String): UUID?` signatures are identical everywhere they're called (Tasks 6, 7, 8).
- **No placeholders:** every step has real, complete code; the one unavoidable manual step (Task 7 Step 12, real Google OAuth browser flow) is explicitly labeled as a manual QA checklist, not a stand-in for an automated test.
