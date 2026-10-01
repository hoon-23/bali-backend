# 푸시 알림 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 루틴 예약 리마인더, 운동 미실행/이탈 알림, 주간·월간 통계 요약을 Expo Push로 발송하는 기능을 구현한다.

**Architecture:** 기존 배치 컨벤션(Airflow DAG → `bali-batch:local` DockerOperator → Spring Boot 배치 앱의 Runner → repository로 DB 직접 접근)을 그대로 따른다. `notification` 도메인은 `bali-core`(포트) / `bali-infra`(JPA 어댑터 + Expo 발송) / `bali-api`(디바이스 토큰·설정 API) / `bali-batch`(Runner)에 패키지로 나뉘어 들어가며, 새 Gradle 모듈은 만들지 않는다.

**Tech Stack:** Kotlin, Spring Boot 3.3.4, Spring Data JPA, Flyway, `org.springframework.web.client.RestClient`(Expo Push API 호출), Airflow 3.3.1(DockerOperator).

**Spec:** `docs/specs/2026-08-25-push-notifications.md`

## Global Constraints

- 도메인 네이밍은 `notification`으로 통일 (패키지: `com.bali.core.notification` / `com.bali.infra.notification` / `com.bali.api.notification` / `com.bali.batch.notification`). 테이블명: `device_tokens`, `notification_settings`, `notification_log`.
- 새 Gradle 모듈을 만들지 않는다. 기존 4개 모듈(`bali-core`/`bali-infra`/`bali-api`/`bali-batch`)에만 패키지로 추가한다.
- Airflow 컨테이너는 타임존 미설정으로 UTC 동작 확인됨. KST 06/18시 = UTC 21/09시, KST 09시 = UTC 00시로 cron을 환산해서 쓴다.
- 영수증(receipt) 조회/갱신 로직은 v1에서 제외한다. `notification_log.delivery_status`는 스키마에만 존재하고 항상 `PENDING`으로 저장된다 (실제 갱신 로직 없음).
- 다음 마이그레이션 번호는 `V17`부터다. `V16__create_monthly_analyses_and_insights_tables.sql`은 `bali-backend-d8` 세션이 작업 디렉토리에 이미 만들어둔 상태(아직 미커밋)라 V16은 이 계획에서 쓰지 않는다. notification 테이블은 `V17`, sessions 인덱스는 `V18`.
- `bali-batch`의 job dispatch(`BaliBatchApplication.main()`의 `args[0]` → Runner 선택)는 이미 구현되어 있다 (`weekly`, `monthly` 지원 중). 여기에 새 job key만 추가한다.
- `bali-infra` 리포지토리 어댑터 테스트는 `@DataJpaTest` + `InfraTestConfig` + 로컬 Postgres(`localhost:5432/bali`) 컨벤션을 따른다 (`WorkoutSessionRepositoryAdapterTest` 참고).
- `bali-batch` Runner 테스트는 `@SpringBootTest(classes = [BaliBatchApplication::class])` + `@Transactional` 컨벤션을 따른다 (`WeeklyAnalysisRunnerTest` 참고).
- `bali-api` 컨트롤러 테스트는 `@SpringBootTest` + `@AutoConfigureMockMvc` + `JwtTokenProvider`로 발급한 토큰 컨벤션을 따른다 (`TemplateControllerTest` 참고).

---

## Task 1: DeviceToken 도메인/인프라 + notification 스키마 마이그레이션

**Files:**
- Create: `bali-infra/src/main/resources/db/migration/V17__create_notification_tables.sql`
- Create: `bali-core/src/main/kotlin/com/bali/core/notification/DevicePlatform.kt`
- Create: `bali-core/src/main/kotlin/com/bali/core/notification/DeviceToken.kt`
- Create: `bali-core/src/main/kotlin/com/bali/core/notification/DeviceTokenRepository.kt`
- Create: `bali-infra/src/main/kotlin/com/bali/infra/notification/DeviceTokenJpaEntity.kt`
- Create: `bali-infra/src/main/kotlin/com/bali/infra/notification/DeviceTokenJpaRepository.kt`
- Create: `bali-infra/src/main/kotlin/com/bali/infra/notification/DeviceTokenRepositoryAdapter.kt`
- Test: `bali-infra/src/test/kotlin/com/bali/infra/notification/DeviceTokenRepositoryAdapterTest.kt`

**Interfaces:**
- Produces: `DeviceToken(id: UUID?, userId: UUID, expoPushToken: String, platform: DevicePlatform, createdAt: Instant, updatedAt: Instant)`, `DevicePlatform { IOS, ANDROID }`, `DeviceTokenRepository { fun upsert(token: DeviceToken): DeviceToken; fun findAllByUserId(userId: UUID): List<DeviceToken>; fun deleteByToken(token: String); fun deleteByUserIdAndToken(userId: UUID, token: String) }` — Task 5(bali-api), Task 7(NotificationDispatcher)가 이 타입/메서드를 그대로 사용한다.

- [ ] **Step 1: 마이그레이션 파일 작성**

`bali-infra/src/main/resources/db/migration/V17__create_notification_tables.sql`:

```sql
CREATE TABLE device_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    expo_push_token VARCHAR(255) NOT NULL,
    platform VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_device_tokens_expo_push_token UNIQUE (expo_push_token)
);

CREATE INDEX idx_device_tokens_user_id ON device_tokens(user_id);

CREATE TABLE notification_settings (
    user_id UUID PRIMARY KEY,
    routine_reminder_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    inactivity_alert_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    summary_notification_enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE notification_log (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    type VARCHAR(30) NOT NULL,
    reference_id UUID,
    expo_ticket_id VARCHAR(255),
    delivery_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    delivery_error VARCHAR(255),
    sent_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_notification_log_type_reference_id ON notification_log(type, reference_id);
CREATE INDEX idx_notification_log_user_id_type_sent_at ON notification_log(user_id, type, sent_at);
```

- [ ] **Step 2: DeviceToken 도메인 작성**

`bali-core/src/main/kotlin/com/bali/core/notification/DevicePlatform.kt`:

```kotlin
package com.bali.core.notification

enum class DevicePlatform { IOS, ANDROID }
```

`bali-core/src/main/kotlin/com/bali/core/notification/DeviceToken.kt`:

```kotlin
package com.bali.core.notification

import java.time.Instant
import java.util.UUID

// 유저가 등록한 Expo push token. 재설치 시 동일 토큰이 다른 유저로 넘어올 수 있어 토큰 자체가 유니크 키
data class DeviceToken(
    val id: UUID?,
    val userId: UUID,
    val expoPushToken: String,
    val platform: DevicePlatform,
    val createdAt: Instant,
    val updatedAt: Instant,
)
```

`bali-core/src/main/kotlin/com/bali/core/notification/DeviceTokenRepository.kt`:

```kotlin
package com.bali.core.notification

import java.util.UUID

// 디바이스 토큰 영속성을 위한 포트 인터페이스
interface DeviceTokenRepository {
    // 토큰 문자열 기준 upsert. 동일 토큰이 이미 있으면 userId/platform을 갱신한다
    fun upsert(token: DeviceToken): DeviceToken

    // 특정 유저의 등록된 토큰 전체 목록
    fun findAllByUserId(userId: UUID): List<DeviceToken>

    // Expo가 DeviceNotRegistered를 응답한 토큰을 정리할 때 사용 (유저 무관하게 토큰 기준 삭제)
    fun deleteByToken(token: String)

    // 클라이언트가 명시적으로 로그아웃/앱 삭제 시 호출. 본인 소유 토큰만 삭제되도록 userId도 매칭
    fun deleteByUserIdAndToken(userId: UUID, token: String)
}
```

- [ ] **Step 3: 실패하는 테스트 작성**

`bali-infra/src/test/kotlin/com/bali/infra/notification/DeviceTokenRepositoryAdapterTest.kt`:

```kotlin
package com.bali.infra.notification

import com.bali.core.notification.DevicePlatform
import com.bali.infra.InfraTestConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Instant
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = [InfraTestConfig::class])
@Import(DeviceTokenRepositoryAdapter::class)
class DeviceTokenRepositoryAdapterTest {

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { "jdbc:postgresql://localhost:5432/bali" }
            registry.add("spring.datasource.username") { "bali" }
            registry.add("spring.datasource.password") { "bali" }
            registry.add("spring.jpa.hibernate.ddl-auto") { "validate" }
        }
    }

    @Autowired lateinit var adapter: DeviceTokenRepositoryAdapter

    @Test
    fun `동일 토큰으로 upsert하면 userId가 갱신된다`() {
        val firstUserId = UUID.randomUUID()
        val secondUserId = UUID.randomUUID()
        val token = "ExponentPushToken[test-${System.nanoTime()}]"

        adapter.upsert(com.bali.core.notification.DeviceToken(id = null, userId = firstUserId, expoPushToken = token, platform = DevicePlatform.IOS, createdAt = Instant.now(), updatedAt = Instant.now()))
        adapter.upsert(com.bali.core.notification.DeviceToken(id = null, userId = secondUserId, expoPushToken = token, platform = DevicePlatform.ANDROID, createdAt = Instant.now(), updatedAt = Instant.now()))

        assertTrue(adapter.findAllByUserId(firstUserId).isEmpty())
        assertEquals(1, adapter.findAllByUserId(secondUserId).size)
        assertEquals(DevicePlatform.ANDROID, adapter.findAllByUserId(secondUserId)[0].platform)
    }

    @Test
    fun `deleteByToken은 유저 무관하게 토큰을 삭제한다`() {
        val userId = UUID.randomUUID()
        val token = "ExponentPushToken[test-${System.nanoTime()}]"
        adapter.upsert(com.bali.core.notification.DeviceToken(id = null, userId = userId, expoPushToken = token, platform = DevicePlatform.IOS, createdAt = Instant.now(), updatedAt = Instant.now()))

        adapter.deleteByToken(token)

        assertTrue(adapter.findAllByUserId(userId).isEmpty())
    }

    @Test
    fun `deleteByUserIdAndToken은 userId가 일치할 때만 삭제한다`() {
        val ownerId = UUID.randomUUID()
        val otherId = UUID.randomUUID()
        val token = "ExponentPushToken[test-${System.nanoTime()}]"
        adapter.upsert(com.bali.core.notification.DeviceToken(id = null, userId = ownerId, expoPushToken = token, platform = DevicePlatform.IOS, createdAt = Instant.now(), updatedAt = Instant.now()))

        adapter.deleteByUserIdAndToken(otherId, token)
        assertEquals(1, adapter.findAllByUserId(ownerId).size)

        adapter.deleteByUserIdAndToken(ownerId, token)
        assertTrue(adapter.findAllByUserId(ownerId).isEmpty())
    }
}
```

- [ ] **Step 4: 테스트가 실패하는지 확인 (컴파일 실패)**

Run: `./gradlew :bali-infra:test --tests "com.bali.infra.notification.DeviceTokenRepositoryAdapterTest"`
Expected: FAIL — `DeviceTokenJpaEntity`/`DeviceTokenJpaRepository`/`DeviceTokenRepositoryAdapter`가 없어 컴파일 에러

- [ ] **Step 5: 인프라 구현**

`bali-infra/src/main/kotlin/com/bali/infra/notification/DeviceTokenJpaEntity.kt`:

```kotlin
package com.bali.infra.notification

import com.bali.core.notification.DevicePlatform
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

// DeviceToken 도메인 모델 영속성을 위한 JPA 엔티티
@Entity
@Table(name = "device_tokens")
class DeviceTokenJpaEntity(
    @Id
    var id: UUID = UUID.randomUUID(),
    var userId: UUID = UUID.randomUUID(),
    var expoPushToken: String = "",
    @Enumerated(EnumType.STRING)
    var platform: DevicePlatform = DevicePlatform.IOS,
    var createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
)
```

`bali-infra/src/main/kotlin/com/bali/infra/notification/DeviceTokenJpaRepository.kt`:

```kotlin
package com.bali.infra.notification

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DeviceTokenJpaRepository : JpaRepository<DeviceTokenJpaEntity, UUID> {
    fun findByExpoPushToken(expoPushToken: String): DeviceTokenJpaEntity?
    fun findAllByUserId(userId: UUID): List<DeviceTokenJpaEntity>
    fun deleteByExpoPushToken(expoPushToken: String)
    fun deleteByUserIdAndExpoPushToken(userId: UUID, expoPushToken: String)
}
```

`bali-infra/src/main/kotlin/com/bali/infra/notification/DeviceTokenRepositoryAdapter.kt`:

```kotlin
package com.bali.infra.notification

import com.bali.core.notification.DeviceToken
import com.bali.core.notification.DeviceTokenRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

// JPA 백엔드로 DeviceTokenRepository 포트를 구현하는 Spring 리포지토리 어댑터
@Repository
class DeviceTokenRepositoryAdapter(
    private val jpaRepository: DeviceTokenJpaRepository,
) : DeviceTokenRepository {

    @Transactional
    override fun upsert(token: DeviceToken): DeviceToken {
        val existing = jpaRepository.findByExpoPushToken(token.expoPushToken)
        val entity = if (existing != null) {
            existing.userId = token.userId
            existing.platform = token.platform
            existing.updatedAt = Instant.now()
            existing
        } else {
            DeviceTokenJpaEntity(
                id = UUID.randomUUID(), userId = token.userId, expoPushToken = token.expoPushToken,
                platform = token.platform, createdAt = Instant.now(), updatedAt = Instant.now(),
            )
        }
        return jpaRepository.save(entity).toDomain()
    }

    override fun findAllByUserId(userId: UUID): List<DeviceToken> =
        jpaRepository.findAllByUserId(userId).map { it.toDomain() }

    @Transactional
    override fun deleteByToken(token: String) {
        jpaRepository.deleteByExpoPushToken(token)
    }

    @Transactional
    override fun deleteByUserIdAndToken(userId: UUID, token: String) {
        jpaRepository.deleteByUserIdAndExpoPushToken(userId, token)
    }

    private fun DeviceTokenJpaEntity.toDomain() = DeviceToken(
        id = id, userId = userId, expoPushToken = expoPushToken, platform = platform,
        createdAt = createdAt, updatedAt = updatedAt,
    )
}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew :bali-infra:test --tests "com.bali.infra.notification.DeviceTokenRepositoryAdapterTest"`
Expected: PASS (3 tests)

- [ ] **Step 7: 커밋**

```bash
git add bali-infra/src/main/resources/db/migration/V17__create_notification_tables.sql \
  bali-core/src/main/kotlin/com/bali/core/notification/DevicePlatform.kt \
  bali-core/src/main/kotlin/com/bali/core/notification/DeviceToken.kt \
  bali-core/src/main/kotlin/com/bali/core/notification/DeviceTokenRepository.kt \
  bali-infra/src/main/kotlin/com/bali/infra/notification/DeviceTokenJpaEntity.kt \
  bali-infra/src/main/kotlin/com/bali/infra/notification/DeviceTokenJpaRepository.kt \
  bali-infra/src/main/kotlin/com/bali/infra/notification/DeviceTokenRepositoryAdapter.kt \
  bali-infra/src/test/kotlin/com/bali/infra/notification/DeviceTokenRepositoryAdapterTest.kt
git commit -m "feat(notification): 디바이스 토큰 등록/조회/삭제 구현"
```

---

## Task 2: NotificationSettings 도메인/인프라

**Files:**
- Create: `bali-core/src/main/kotlin/com/bali/core/notification/NotificationSettings.kt`
- Create: `bali-core/src/main/kotlin/com/bali/core/notification/NotificationSettingsRepository.kt`
- Create: `bali-infra/src/main/kotlin/com/bali/infra/notification/NotificationSettingsJpaEntity.kt`
- Create: `bali-infra/src/main/kotlin/com/bali/infra/notification/NotificationSettingsJpaRepository.kt`
- Create: `bali-infra/src/main/kotlin/com/bali/infra/notification/NotificationSettingsRepositoryAdapter.kt`
- Test: `bali-infra/src/test/kotlin/com/bali/infra/notification/NotificationSettingsRepositoryAdapterTest.kt`

**Interfaces:**
- Produces: `NotificationSettings(userId: UUID, routineReminderEnabled: Boolean = true, inactivityAlertEnabled: Boolean = true, summaryNotificationEnabled: Boolean = true)`, `NotificationSettings.defaults(userId: UUID): NotificationSettings`, `NotificationSettingsRepository { fun findByUserId(userId: UUID): NotificationSettings?; fun save(settings: NotificationSettings): NotificationSettings }` — Task 5, Task 8~11이 사용한다. `findByUserId`가 null이면 호출부가 `NotificationSettings.defaults(userId)`로 취급한다 (행 없는 유저는 전부 켜짐).

- [ ] **Step 1: 도메인 작성**

`bali-core/src/main/kotlin/com/bali/core/notification/NotificationSettings.kt`:

```kotlin
package com.bali.core.notification

import java.util.UUID

// 유저별 알림 유형 on/off 설정. 행이 없는 유저는 defaults()로 전부 켜진 상태로 취급한다
data class NotificationSettings(
    val userId: UUID,
    val routineReminderEnabled: Boolean = true,
    val inactivityAlertEnabled: Boolean = true,
    val summaryNotificationEnabled: Boolean = true,
) {
    companion object {
        fun defaults(userId: UUID) = NotificationSettings(userId)
    }
}
```

`bali-core/src/main/kotlin/com/bali/core/notification/NotificationSettingsRepository.kt`:

```kotlin
package com.bali.core.notification

import java.util.UUID

// 알림 설정 영속성을 위한 포트 인터페이스
interface NotificationSettingsRepository {
    // 행이 없으면 null (호출부가 NotificationSettings.defaults(userId)로 취급)
    fun findByUserId(userId: UUID): NotificationSettings?
    fun save(settings: NotificationSettings): NotificationSettings
}
```

- [ ] **Step 2: 실패하는 테스트 작성**

`bali-infra/src/test/kotlin/com/bali/infra/notification/NotificationSettingsRepositoryAdapterTest.kt`:

```kotlin
package com.bali.infra.notification

import com.bali.core.notification.NotificationSettings
import com.bali.infra.InfraTestConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = [InfraTestConfig::class])
@Import(NotificationSettingsRepositoryAdapter::class)
class NotificationSettingsRepositoryAdapterTest {

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { "jdbc:postgresql://localhost:5432/bali" }
            registry.add("spring.datasource.username") { "bali" }
            registry.add("spring.datasource.password") { "bali" }
            registry.add("spring.jpa.hibernate.ddl-auto") { "validate" }
        }
    }

    @Autowired lateinit var adapter: NotificationSettingsRepositoryAdapter

    @Test
    fun `행이 없는 유저는 null을 반환한다`() {
        assertNull(adapter.findByUserId(UUID.randomUUID()))
    }

    @Test
    fun `저장한 설정을 그대로 조회할 수 있다`() {
        val userId = UUID.randomUUID()
        adapter.save(NotificationSettings(userId = userId, routineReminderEnabled = false, inactivityAlertEnabled = true, summaryNotificationEnabled = false))

        val found = adapter.findByUserId(userId)

        assertEquals(false, found?.routineReminderEnabled)
        assertEquals(true, found?.inactivityAlertEnabled)
        assertEquals(false, found?.summaryNotificationEnabled)
    }
}
```

- [ ] **Step 3: 테스트가 실패하는지 확인**

Run: `./gradlew :bali-infra:test --tests "com.bali.infra.notification.NotificationSettingsRepositoryAdapterTest"`
Expected: FAIL — 컴파일 에러 (`NotificationSettingsRepositoryAdapter` 없음)

- [ ] **Step 4: 인프라 구현**

`bali-infra/src/main/kotlin/com/bali/infra/notification/NotificationSettingsJpaEntity.kt`:

```kotlin
package com.bali.infra.notification

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

// NotificationSettings 도메인 모델 영속성을 위한 JPA 엔티티. userId 자체가 PK (1:1)
@Entity
@Table(name = "notification_settings")
class NotificationSettingsJpaEntity(
    @Id
    var userId: UUID = UUID.randomUUID(),
    var routineReminderEnabled: Boolean = true,
    var inactivityAlertEnabled: Boolean = true,
    var summaryNotificationEnabled: Boolean = true,
)
```

`bali-infra/src/main/kotlin/com/bali/infra/notification/NotificationSettingsJpaRepository.kt`:

```kotlin
package com.bali.infra.notification

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NotificationSettingsJpaRepository : JpaRepository<NotificationSettingsJpaEntity, UUID>
```

`bali-infra/src/main/kotlin/com/bali/infra/notification/NotificationSettingsRepositoryAdapter.kt`:

```kotlin
package com.bali.infra.notification

import com.bali.core.notification.NotificationSettings
import com.bali.core.notification.NotificationSettingsRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// JPA 백엔드로 NotificationSettingsRepository 포트를 구현하는 Spring 리포지토리 어댑터
@Repository
class NotificationSettingsRepositoryAdapter(
    private val jpaRepository: NotificationSettingsJpaRepository,
) : NotificationSettingsRepository {

    override fun findByUserId(userId: UUID): NotificationSettings? =
        jpaRepository.findById(userId).orElse(null)?.toDomain()

    @Transactional
    override fun save(settings: NotificationSettings): NotificationSettings =
        jpaRepository.save(
            NotificationSettingsJpaEntity(
                userId = settings.userId,
                routineReminderEnabled = settings.routineReminderEnabled,
                inactivityAlertEnabled = settings.inactivityAlertEnabled,
                summaryNotificationEnabled = settings.summaryNotificationEnabled,
            )
        ).toDomain()

    private fun NotificationSettingsJpaEntity.toDomain() = NotificationSettings(
        userId = userId, routineReminderEnabled = routineReminderEnabled,
        inactivityAlertEnabled = inactivityAlertEnabled, summaryNotificationEnabled = summaryNotificationEnabled,
    )
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :bali-infra:test --tests "com.bali.infra.notification.NotificationSettingsRepositoryAdapterTest"`
Expected: PASS (2 tests)

- [ ] **Step 6: 커밋**

```bash
git add bali-core/src/main/kotlin/com/bali/core/notification/NotificationSettings.kt \
  bali-core/src/main/kotlin/com/bali/core/notification/NotificationSettingsRepository.kt \
  bali-infra/src/main/kotlin/com/bali/infra/notification/NotificationSettingsJpaEntity.kt \
  bali-infra/src/main/kotlin/com/bali/infra/notification/NotificationSettingsJpaRepository.kt \
  bali-infra/src/main/kotlin/com/bali/infra/notification/NotificationSettingsRepositoryAdapter.kt \
  bali-infra/src/test/kotlin/com/bali/infra/notification/NotificationSettingsRepositoryAdapterTest.kt
git commit -m "feat(notification): 알림 유형별 설정 조회/저장 구현"
```

---

## Task 3: NotificationLog 도메인/인프라

**Files:**
- Create: `bali-core/src/main/kotlin/com/bali/core/notification/NotificationType.kt`
- Create: `bali-core/src/main/kotlin/com/bali/core/notification/DeliveryStatus.kt`
- Create: `bali-core/src/main/kotlin/com/bali/core/notification/NotificationLog.kt`
- Create: `bali-core/src/main/kotlin/com/bali/core/notification/NotificationLogRepository.kt`
- Create: `bali-infra/src/main/kotlin/com/bali/infra/notification/NotificationLogJpaEntity.kt`
- Create: `bali-infra/src/main/kotlin/com/bali/infra/notification/NotificationLogJpaRepository.kt`
- Create: `bali-infra/src/main/kotlin/com/bali/infra/notification/NotificationLogRepositoryAdapter.kt`
- Test: `bali-infra/src/test/kotlin/com/bali/infra/notification/NotificationLogRepositoryAdapterTest.kt`

**Interfaces:**
- Produces: `NotificationType { ROUTINE_REMINDER, INACTIVITY_ALERT, WEEKLY_SUMMARY, MONTHLY_SUMMARY }`, `DeliveryStatus { PENDING, DELIVERED, FAILED }`, `NotificationLog(id: UUID?, userId: UUID, type: NotificationType, referenceId: UUID?, expoTicketId: String?, deliveryStatus: DeliveryStatus, deliveryError: String?, sentAt: Instant)`, `NotificationLogRepository { fun existsByTypeAndReferenceId(type: NotificationType, referenceId: UUID): Boolean; fun existsByUserIdAndTypeSentAfter(userId: UUID, type: NotificationType, after: Instant): Boolean; fun save(log: NotificationLog): NotificationLog }` — Task 7(NotificationDispatcher), Task 8~11(Runner의 dedup 체크)이 사용한다.

- [ ] **Step 1: 도메인 작성**

`bali-core/src/main/kotlin/com/bali/core/notification/NotificationType.kt`:

```kotlin
package com.bali.core.notification

enum class NotificationType { ROUTINE_REMINDER, INACTIVITY_ALERT, WEEKLY_SUMMARY, MONTHLY_SUMMARY }
```

`bali-core/src/main/kotlin/com/bali/core/notification/DeliveryStatus.kt`:

```kotlin
package com.bali.core.notification

// 영수증(receipt) 조회 결과를 담는 기록용 상태. v1은 이 상태를 갱신하는 로직이 없어 항상 PENDING으로 남는다
enum class DeliveryStatus { PENDING, DELIVERED, FAILED }
```

`bali-core/src/main/kotlin/com/bali/core/notification/NotificationLog.kt`:

```kotlin
package com.bali.core.notification

import java.time.Instant
import java.util.UUID

// 알림 발송 이력. 중복 발송 방지(idempotency)와 발송 기록 조회를 겸한다
data class NotificationLog(
    val id: UUID?,
    val userId: UUID,
    val type: NotificationType,
    val referenceId: UUID?,
    val expoTicketId: String?,
    val deliveryStatus: DeliveryStatus,
    val deliveryError: String?,
    val sentAt: Instant,
)
```

`bali-core/src/main/kotlin/com/bali/core/notification/NotificationLogRepository.kt`:

```kotlin
package com.bali.core.notification

import java.time.Instant
import java.util.UUID

// 알림 발송 이력 영속성을 위한 포트 인터페이스
interface NotificationLogRepository {
    // ROUTINE_REMINDER/WEEKLY_SUMMARY/MONTHLY_SUMMARY 중복 발송 방지: 동일 (type, referenceId) 발송 여부
    fun existsByTypeAndReferenceId(type: NotificationType, referenceId: UUID): Boolean

    // INACTIVITY_ALERT 쿨다운 체크: after 시각 이후 동일 (userId, type) 발송 여부
    fun existsByUserIdAndTypeSentAfter(userId: UUID, type: NotificationType, after: Instant): Boolean

    fun save(log: NotificationLog): NotificationLog
}
```

- [ ] **Step 2: 실패하는 테스트 작성**

`bali-infra/src/test/kotlin/com/bali/infra/notification/NotificationLogRepositoryAdapterTest.kt`:

```kotlin
package com.bali.infra.notification

import com.bali.core.notification.DeliveryStatus
import com.bali.core.notification.NotificationLog
import com.bali.core.notification.NotificationType
import com.bali.infra.InfraTestConfig
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = [InfraTestConfig::class])
@Import(NotificationLogRepositoryAdapter::class)
class NotificationLogRepositoryAdapterTest {

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { "jdbc:postgresql://localhost:5432/bali" }
            registry.add("spring.datasource.username") { "bali" }
            registry.add("spring.datasource.password") { "bali" }
            registry.add("spring.jpa.hibernate.ddl-auto") { "validate" }
        }
    }

    @Autowired lateinit var adapter: NotificationLogRepositoryAdapter

    @Test
    fun `동일 type reference_id로 저장하면 existsByTypeAndReferenceId가 true를 반환한다`() {
        val referenceId = UUID.randomUUID()
        adapter.save(NotificationLog(id = null, userId = UUID.randomUUID(), type = NotificationType.ROUTINE_REMINDER, referenceId = referenceId, expoTicketId = "ticket-1", deliveryStatus = DeliveryStatus.PENDING, deliveryError = null, sentAt = Instant.now()))

        assertTrue(adapter.existsByTypeAndReferenceId(NotificationType.ROUTINE_REMINDER, referenceId))
        assertFalse(adapter.existsByTypeAndReferenceId(NotificationType.INACTIVITY_ALERT, referenceId))
    }

    @Test
    fun `sentAt이 기준 시각 이후면 existsByUserIdAndTypeSentAfter가 true를 반환한다`() {
        val userId = UUID.randomUUID()
        adapter.save(NotificationLog(id = null, userId = userId, type = NotificationType.INACTIVITY_ALERT, referenceId = null, expoTicketId = "ticket-2", deliveryStatus = DeliveryStatus.PENDING, deliveryError = null, sentAt = Instant.now()))

        assertTrue(adapter.existsByUserIdAndTypeSentAfter(userId, NotificationType.INACTIVITY_ALERT, Instant.now().minus(1, ChronoUnit.DAYS)))
        assertFalse(adapter.existsByUserIdAndTypeSentAfter(userId, NotificationType.INACTIVITY_ALERT, Instant.now().plus(1, ChronoUnit.DAYS)))
    }
}
```

- [ ] **Step 3: 테스트가 실패하는지 확인**

Run: `./gradlew :bali-infra:test --tests "com.bali.infra.notification.NotificationLogRepositoryAdapterTest"`
Expected: FAIL — 컴파일 에러

- [ ] **Step 4: 인프라 구현**

`bali-infra/src/main/kotlin/com/bali/infra/notification/NotificationLogJpaEntity.kt`:

```kotlin
package com.bali.infra.notification

import com.bali.core.notification.DeliveryStatus
import com.bali.core.notification.NotificationType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

// NotificationLog 도메인 모델 영속성을 위한 JPA 엔티티
@Entity
@Table(name = "notification_log")
class NotificationLogJpaEntity(
    @Id
    var id: UUID = UUID.randomUUID(),
    var userId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    var type: NotificationType = NotificationType.ROUTINE_REMINDER,
    var referenceId: UUID? = null,
    var expoTicketId: String? = null,
    @Enumerated(EnumType.STRING)
    var deliveryStatus: DeliveryStatus = DeliveryStatus.PENDING,
    var deliveryError: String? = null,
    var sentAt: Instant = Instant.now(),
)
```

`bali-infra/src/main/kotlin/com/bali/infra/notification/NotificationLogJpaRepository.kt`:

```kotlin
package com.bali.infra.notification

import com.bali.core.notification.NotificationType
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface NotificationLogJpaRepository : JpaRepository<NotificationLogJpaEntity, UUID> {
    fun existsByTypeAndReferenceId(type: NotificationType, referenceId: UUID): Boolean
    fun existsByUserIdAndTypeAndSentAtAfter(userId: UUID, type: NotificationType, sentAt: Instant): Boolean
}
```

`bali-infra/src/main/kotlin/com/bali/infra/notification/NotificationLogRepositoryAdapter.kt`:

```kotlin
package com.bali.infra.notification

import com.bali.core.notification.NotificationLog
import com.bali.core.notification.NotificationLogRepository
import com.bali.core.notification.NotificationType
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

// JPA 백엔드로 NotificationLogRepository 포트를 구현하는 Spring 리포지토리 어댑터
@Repository
class NotificationLogRepositoryAdapter(
    private val jpaRepository: NotificationLogJpaRepository,
) : NotificationLogRepository {

    override fun existsByTypeAndReferenceId(type: NotificationType, referenceId: UUID): Boolean =
        jpaRepository.existsByTypeAndReferenceId(type, referenceId)

    override fun existsByUserIdAndTypeSentAfter(userId: UUID, type: NotificationType, after: Instant): Boolean =
        jpaRepository.existsByUserIdAndTypeAndSentAtAfter(userId, type, after)

    @Transactional
    override fun save(log: NotificationLog): NotificationLog =
        jpaRepository.save(
            NotificationLogJpaEntity(
                id = log.id ?: UUID.randomUUID(), userId = log.userId, type = log.type,
                referenceId = log.referenceId, expoTicketId = log.expoTicketId,
                deliveryStatus = log.deliveryStatus, deliveryError = log.deliveryError, sentAt = log.sentAt,
            )
        ).toDomain()

    private fun NotificationLogJpaEntity.toDomain() = NotificationLog(
        id = id, userId = userId, type = type, referenceId = referenceId, expoTicketId = expoTicketId,
        deliveryStatus = deliveryStatus, deliveryError = deliveryError, sentAt = sentAt,
    )
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :bali-infra:test --tests "com.bali.infra.notification.NotificationLogRepositoryAdapterTest"`
Expected: PASS (2 tests)

- [ ] **Step 6: 커밋**

```bash
git add bali-core/src/main/kotlin/com/bali/core/notification/NotificationType.kt \
  bali-core/src/main/kotlin/com/bali/core/notification/DeliveryStatus.kt \
  bali-core/src/main/kotlin/com/bali/core/notification/NotificationLog.kt \
  bali-core/src/main/kotlin/com/bali/core/notification/NotificationLogRepository.kt \
  bali-infra/src/main/kotlin/com/bali/infra/notification/NotificationLogJpaEntity.kt \
  bali-infra/src/main/kotlin/com/bali/infra/notification/NotificationLogJpaRepository.kt \
  bali-infra/src/main/kotlin/com/bali/infra/notification/NotificationLogRepositoryAdapter.kt \
  bali-infra/src/test/kotlin/com/bali/infra/notification/NotificationLogRepositoryAdapterTest.kt
git commit -m "feat(notification): 알림 발송 이력(중복 방지/기록) 구현"
```

---

## Task 4: NotificationSender 포트 + ExpoPushSender

**Files:**
- Create: `bali-core/src/main/kotlin/com/bali/core/notification/NotificationSender.kt`
- Modify: `bali-infra/build.gradle.kts` (RestClient를 쓰기 위해 `spring-web` 추가)
- Create: `bali-infra/src/main/kotlin/com/bali/infra/notification/ExpoPushSender.kt`
- Create: `bali-infra/src/main/kotlin/com/bali/infra/notification/NotificationInfraConfig.kt`
- Test: `bali-infra/src/test/kotlin/com/bali/infra/notification/ExpoPushSenderTest.kt`

**Interfaces:**
- Produces: `PushMessage(token: String, title: String, body: String, data: Map<String,String> = emptyMap())`, `PushSendResult(token: String, ticketId: String?, error: PushSendError?)`, `PushSendError { DEVICE_NOT_REGISTERED, OTHER }`, `fun interface NotificationSender { fun send(messages: List<PushMessage>): List<PushSendResult> }` — Task 7(NotificationDispatcher)이 이 포트를 주입받아 사용한다. `notificationSender()` `@Bean`은 `NotificationInfraConfig`(bali-infra)에 등록되어 `bali-batch`의 Spring 컨텍스트에서 자동으로 주입 가능하다.

- [ ] **Step 1: 포트 작성**

`bali-core/src/main/kotlin/com/bali/core/notification/NotificationSender.kt`:

```kotlin
package com.bali.core.notification

// 알림 발송 포트. 실전 구현은 Expo Push API 호출(ExpoPushSender), 테스트는 fake로 대체
fun interface NotificationSender {
    fun send(messages: List<PushMessage>): List<PushSendResult>
}

data class PushMessage(val token: String, val title: String, val body: String, val data: Map<String, String> = emptyMap())

data class PushSendResult(val token: String, val ticketId: String?, val error: PushSendError?)

enum class PushSendError { DEVICE_NOT_REGISTERED, OTHER }
```

- [ ] **Step 2: bali-infra에 spring-web 의존성 추가**

`bali-infra/build.gradle.kts`의 `dependencies` 블록에 추가 (`implementation("org.springframework.boot:spring-boot-starter-data-jpa")` 바로 아래):

```kotlin
    implementation("org.springframework:spring-web")
```

- [ ] **Step 3: 실패하는 테스트 작성**

`bali-infra/src/test/kotlin/com/bali/infra/notification/ExpoPushSenderTest.kt`:

```kotlin
package com.bali.infra.notification

import com.bali.core.notification.PushMessage
import com.bali.core.notification.PushSendError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class ExpoPushSenderTest {

    @Test
    fun `정상 발송 시 티켓ID를 반환한다`() {
        val builder = RestClient.builder()
        val mockServer = MockRestServiceServer.bindTo(builder).build()
        val restClient = builder.build()

        mockServer.expect(requestTo("https://exp.host/--/api/v2/push/send"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""{"data":[{"status":"ok","id":"ticket-1"}]}""", MediaType.APPLICATION_JSON))

        val sender = ExpoPushSender(restClient)
        val results = sender.send(listOf(PushMessage(token = "ExponentPushToken[abc]", title = "제목", body = "본문")))

        assertEquals(1, results.size)
        assertEquals("ticket-1", results[0].ticketId)
        assertNull(results[0].error)
        mockServer.verify()
    }

    @Test
    fun `DeviceNotRegistered 에러는 DEVICE_NOT_REGISTERED로 매핑된다`() {
        val builder = RestClient.builder()
        val mockServer = MockRestServiceServer.bindTo(builder).build()
        val restClient = builder.build()

        mockServer.expect(requestTo("https://exp.host/--/api/v2/push/send"))
            .andRespond(withSuccess("""{"data":[{"status":"error","message":"not registered","details":{"error":"DeviceNotRegistered"}}]}""", MediaType.APPLICATION_JSON))

        val sender = ExpoPushSender(restClient)
        val results = sender.send(listOf(PushMessage(token = "ExponentPushToken[dead]", title = "제목", body = "본문")))

        assertEquals(PushSendError.DEVICE_NOT_REGISTERED, results[0].error)
        assertNull(results[0].ticketId)
        mockServer.verify()
    }

    @Test
    fun `101개 메시지는 100개씩 두 번의 배치 요청으로 나뉜다`() {
        val builder = RestClient.builder()
        val mockServer = MockRestServiceServer.bindTo(builder).build()
        val restClient = builder.build()

        val firstBatchTickets = (1..100).joinToString(",") { """{"status":"ok","id":"ticket-$it"}""" }
        mockServer.expect(requestTo("https://exp.host/--/api/v2/push/send"))
            .andRespond(withSuccess("""{"data":[$firstBatchTickets]}""", MediaType.APPLICATION_JSON))
        mockServer.expect(requestTo("https://exp.host/--/api/v2/push/send"))
            .andRespond(withSuccess("""{"data":[{"status":"ok","id":"ticket-101"}]}""", MediaType.APPLICATION_JSON))

        val sender = ExpoPushSender(restClient)
        val messages = (1..101).map { PushMessage(token = "ExponentPushToken[$it]", title = "제목", body = "본문") }
        val results = sender.send(messages)

        assertEquals(101, results.size)
        assertEquals("ticket-101", results[100].ticketId)
        mockServer.verify()
    }
}
```

- [ ] **Step 4: 테스트가 실패하는지 확인**

Run: `./gradlew :bali-infra:test --tests "com.bali.infra.notification.ExpoPushSenderTest"`
Expected: FAIL — 컴파일 에러 (`ExpoPushSender` 없음)

- [ ] **Step 5: ExpoPushSender 구현**

`bali-infra/src/main/kotlin/com/bali/infra/notification/ExpoPushSender.kt`:

```kotlin
package com.bali.infra.notification

import com.bali.core.notification.NotificationSender
import com.bali.core.notification.PushMessage
import com.bali.core.notification.PushSendError
import com.bali.core.notification.PushSendResult
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.springframework.web.client.RestClient

@JsonIgnoreProperties(ignoreUnknown = true)
data class ExpoTicketDetails(val error: String? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ExpoTicket(val status: String = "error", val id: String? = null, val message: String? = null, val details: ExpoTicketDetails? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ExpoPushSendResponse(val data: List<ExpoTicket> = emptyList())

data class ExpoPushMessageRequest(val to: String, val title: String, val body: String, val data: Map<String, String> = emptyMap())

// Expo Push API로 알림을 발송하는 NotificationSender 구현. 최대 100개씩 배치 요청한다(Expo 제약)
class ExpoPushSender(private val restClient: RestClient) : NotificationSender {

    companion object {
        private const val BATCH_SIZE = 100
        private const val EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send"
    }

    override fun send(messages: List<PushMessage>): List<PushSendResult> =
        messages.chunked(BATCH_SIZE).flatMap { sendBatch(it) }

    // Expo는 요청 배열과 동일한 순서로 티켓 배열을 반환한다고 문서화되어 있어 zip으로 매칭한다
    private fun sendBatch(batch: List<PushMessage>): List<PushSendResult> {
        val response = restClient.post()
            .uri(EXPO_PUSH_URL)
            .body(batch.map { ExpoPushMessageRequest(to = it.token, title = it.title, body = it.body, data = it.data) })
            .retrieve()
            .body(ExpoPushSendResponse::class.java) ?: ExpoPushSendResponse()

        return batch.zip(response.data).map { (message, ticket) ->
            if (ticket.status == "ok") {
                PushSendResult(token = message.token, ticketId = ticket.id, error = null)
            } else {
                val error = if (ticket.details?.error == "DeviceNotRegistered") PushSendError.DEVICE_NOT_REGISTERED else PushSendError.OTHER
                PushSendResult(token = message.token, ticketId = null, error = error)
            }
        }
    }
}
```

`bali-infra/src/main/kotlin/com/bali/infra/notification/NotificationInfraConfig.kt`:

```kotlin
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
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew :bali-infra:test --tests "com.bali.infra.notification.ExpoPushSenderTest"`
Expected: PASS (3 tests)

- [ ] **Step 7: 커밋**

```bash
git add bali-core/src/main/kotlin/com/bali/core/notification/NotificationSender.kt \
  bali-infra/build.gradle.kts \
  bali-infra/src/main/kotlin/com/bali/infra/notification/ExpoPushSender.kt \
  bali-infra/src/main/kotlin/com/bali/infra/notification/NotificationInfraConfig.kt \
  bali-infra/src/test/kotlin/com/bali/infra/notification/ExpoPushSenderTest.kt
git commit -m "feat(notification): Expo Push API 발송 어댑터 구현"
```

---

## Task 5: bali-api — 디바이스 토큰/설정 API

**Files:**
- Create: `bali-api/src/main/kotlin/com/bali/api/notification/DeviceTokenRequest.kt`
- Create: `bali-api/src/main/kotlin/com/bali/api/notification/NotificationSettingsResponse.kt`
- Create: `bali-api/src/main/kotlin/com/bali/api/notification/NotificationSettingsPatchRequest.kt`
- Create: `bali-api/src/main/kotlin/com/bali/api/notification/NotificationController.kt`
- Test: `bali-api/src/test/kotlin/com/bali/api/notification/NotificationControllerTest.kt`

**Interfaces:**
- Consumes: `DeviceTokenRepository`(Task 1), `NotificationSettingsRepository`(Task 2), `NotificationSettings.defaults(userId)`(Task 2)
- Produces: `POST/DELETE /api/v1/notifications/device-token`, `GET/PATCH /api/v1/notifications/settings` — bali-frontend가 사용할 엔드포인트. `SecurityConfig`는 이미 `anyRequest -> authenticated`이므로 별도 설정 변경 불필요.

- [ ] **Step 1: 요청/응답 DTO 작성**

`bali-api/src/main/kotlin/com/bali/api/notification/DeviceTokenRequest.kt`:

```kotlin
package com.bali.api.notification

import com.bali.core.notification.DevicePlatform

data class DeviceTokenRequest(val token: String, val platform: DevicePlatform)

data class DeviceTokenDeleteRequest(val token: String)
```

`bali-api/src/main/kotlin/com/bali/api/notification/NotificationSettingsResponse.kt`:

```kotlin
package com.bali.api.notification

import com.bali.core.notification.NotificationSettings

data class NotificationSettingsResponse(
    val routineReminderEnabled: Boolean,
    val inactivityAlertEnabled: Boolean,
    val summaryNotificationEnabled: Boolean,
) {
    companion object {
        fun from(settings: NotificationSettings) = NotificationSettingsResponse(
            routineReminderEnabled = settings.routineReminderEnabled,
            inactivityAlertEnabled = settings.inactivityAlertEnabled,
            summaryNotificationEnabled = settings.summaryNotificationEnabled,
        )
    }
}
```

`bali-api/src/main/kotlin/com/bali/api/notification/NotificationSettingsPatchRequest.kt`:

```kotlin
package com.bali.api.notification

// null인 필드는 변경하지 않음 (SessionPatchRequest와 동일한 부분 수정 관례)
data class NotificationSettingsPatchRequest(
    val routineReminderEnabled: Boolean? = null,
    val inactivityAlertEnabled: Boolean? = null,
    val summaryNotificationEnabled: Boolean? = null,
)
```

- [ ] **Step 2: 실패하는 테스트 작성**

`bali-api/src/test/kotlin/com/bali/api/notification/NotificationControllerTest.kt`:

```kotlin
package com.bali.api.notification

import com.bali.api.auth.jwt.JwtTokenProvider
import com.bali.core.notification.DeviceTokenRepository
import com.bali.core.notification.NotificationSettingsRepository
import com.bali.core.user.AuthProvider
import com.bali.infra.user.UserJpaEntity
import com.bali.infra.user.UserJpaRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired lateinit var userJpaRepository: UserJpaRepository
    @Autowired lateinit var deviceTokenRepository: DeviceTokenRepository
    @Autowired lateinit var settingsRepository: NotificationSettingsRepository

    private fun issueTokenForNewUser(): Pair<String, UUID> {
        val entity = userJpaRepository.save(
            UserJpaEntity(email = "notif-test-${System.nanoTime()}@example.com", provider = AuthProvider.GOOGLE, providerId = "sub-notif-${System.nanoTime()}")
        )
        return jwtTokenProvider.generateToken(entity.id, entity.email) to entity.id
    }

    @Test
    fun `POST device-token 호출하면 토큰을 등록한다`() {
        val (token, userId) = issueTokenForNewUser()
        val body = """{"token":"ExponentPushToken[api-1]","platform":"IOS"}"""

        mockMvc.perform(post("/api/v1/notifications/device-token").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isNoContent)

        assertEquals(1, deviceTokenRepository.findAllByUserId(userId).size)
    }

    @Test
    fun `DELETE device-token 호출하면 토큰을 제거한다`() {
        val (token, userId) = issueTokenForNewUser()
        mockMvc.perform(post("/api/v1/notifications/device-token").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content("""{"token":"ExponentPushToken[api-2]","platform":"IOS"}"""))
            .andExpect(status().isNoContent)

        mockMvc.perform(delete("/api/v1/notifications/device-token").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content("""{"token":"ExponentPushToken[api-2]"}"""))
            .andExpect(status().isNoContent)

        assertTrue(deviceTokenRepository.findAllByUserId(userId).isEmpty())
    }

    @Test
    fun `GET settings 호출시 설정이 없으면 기본값을 반환한다`() {
        val (token, _) = issueTokenForNewUser()

        mockMvc.perform(get("/api/v1/notifications/settings").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.routineReminderEnabled").value(true))
            .andExpect(jsonPath("$.inactivityAlertEnabled").value(true))
            .andExpect(jsonPath("$.summaryNotificationEnabled").value(true))
    }

    @Test
    fun `PATCH settings 호출하면 지정한 필드만 변경된다`() {
        val (token, _) = issueTokenForNewUser()

        mockMvc.perform(patch("/api/v1/notifications/settings").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content("""{"routineReminderEnabled":false}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.routineReminderEnabled").value(false))
            .andExpect(jsonPath("$.inactivityAlertEnabled").value(true))
    }
}
```

- [ ] **Step 3: 테스트가 실패하는지 확인**

Run: `./gradlew :bali-api:test --tests "com.bali.api.notification.NotificationControllerTest"`
Expected: FAIL — 컴파일 에러 (`NotificationController` 없음, 404)

- [ ] **Step 4: 컨트롤러 구현**

`bali-api/src/main/kotlin/com/bali/api/notification/NotificationController.kt`:

```kotlin
package com.bali.api.notification

import com.bali.core.notification.DeviceToken
import com.bali.core.notification.DeviceTokenRepository
import com.bali.core.notification.NotificationSettings
import com.bali.core.notification.NotificationSettingsRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

// 디바이스 토큰 등록/삭제, 알림 유형별 설정 조회/변경 API 엔드포인트를 처리하는 REST 컨트롤러
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notification", description = "디바이스 토큰 및 알림 설정 API")
class NotificationController(
    private val deviceTokenRepository: DeviceTokenRepository,
    private val settingsRepository: NotificationSettingsRepository,
) {

    // Expo push token 등록/갱신. 토큰 기준 upsert이므로 재등록해도 안전
    @Operation(summary = "디바이스 토큰 등록", description = "Expo push token을 등록/갱신한다(토큰 기준 upsert)")
    @PostMapping("/device-token")
    fun registerToken(@RequestBody request: DeviceTokenRequest): ResponseEntity<Void> {
        deviceTokenRepository.upsert(
            DeviceToken(
                id = null, userId = currentUserId(), expoPushToken = request.token, platform = request.platform,
                createdAt = Instant.now(), updatedAt = Instant.now(),
            )
        )
        return ResponseEntity.noContent().build()
    }

    // 로그아웃/앱 삭제 시 토큰 제거. 본인 소유 토큰만 삭제된다
    @Operation(summary = "디바이스 토큰 삭제", description = "로그아웃/앱 삭제 시 본인 소유 토큰을 제거한다")
    @DeleteMapping("/device-token")
    fun deleteToken(@RequestBody request: DeviceTokenDeleteRequest): ResponseEntity<Void> {
        deviceTokenRepository.deleteByUserIdAndToken(currentUserId(), request.token)
        return ResponseEntity.noContent().build()
    }

    // 알림 유형별 on/off 설정 조회. 행이 없으면 기본값(전부 true)으로 응답
    @Operation(summary = "알림 설정 조회", description = "행이 없으면 기본값(전부 true)으로 응답한다")
    @GetMapping("/settings")
    fun getSettings(): NotificationSettingsResponse =
        NotificationSettingsResponse.from(settingsRepository.findByUserId(currentUserId()) ?: NotificationSettings.defaults(currentUserId()))

    // 알림 유형별 on/off 설정 변경. null인 필드는 기존 값 유지
    @Operation(summary = "알림 설정 변경", description = "지정한 필드만 변경하고 나머지는 기존 값(없으면 기본값)을 유지한다")
    @PatchMapping("/settings")
    fun patchSettings(@RequestBody request: NotificationSettingsPatchRequest): NotificationSettingsResponse {
        val current = settingsRepository.findByUserId(currentUserId()) ?: NotificationSettings.defaults(currentUserId())
        val updated = current.copy(
            routineReminderEnabled = request.routineReminderEnabled ?: current.routineReminderEnabled,
            inactivityAlertEnabled = request.inactivityAlertEnabled ?: current.inactivityAlertEnabled,
            summaryNotificationEnabled = request.summaryNotificationEnabled ?: current.summaryNotificationEnabled,
        )
        return NotificationSettingsResponse.from(settingsRepository.save(updated))
    }

    // SecurityContextHolder에서 현재 인증된 사용자의 UUID를 추출
    private fun currentUserId(): UUID =
        UUID.fromString(SecurityContextHolder.getContext().authentication.name)
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :bali-api:test --tests "com.bali.api.notification.NotificationControllerTest"`
Expected: PASS (4 tests)

- [ ] **Step 6: 커밋**

```bash
git add bali-api/src/main/kotlin/com/bali/api/notification/ \
  bali-api/src/test/kotlin/com/bali/api/notification/NotificationControllerTest.kt
git commit -m "feat(notification): 디바이스 토큰/알림 설정 API 추가"
```

---

## Task 6: WorkoutSessionRepository 확장 (리마인더/이탈 알림용 조회)

**Files:**
- Create: `bali-infra/src/main/resources/db/migration/V18__add_sessions_date_status_index.sql`
- Modify: `bali-core/src/main/kotlin/com/bali/core/session/WorkoutSessionRepository.kt`
- Modify: `bali-infra/src/main/kotlin/com/bali/infra/session/WorkoutSessionJpaRepository.kt`
- Modify: `bali-infra/src/main/kotlin/com/bali/infra/session/WorkoutSessionRepositoryAdapter.kt`
- Modify: `bali-infra/src/test/kotlin/com/bali/infra/session/WorkoutSessionRepositoryAdapterTest.kt`

**Interfaces:**
- Produces: `WorkoutSessionRepository.findAllByDateAndStatus(date: LocalDate, status: SessionStatus): List<WorkoutSession>`, `WorkoutSessionRepository.findLastActiveDate(userId: UUID): LocalDate?` — Task 8(RoutineReminderRunner), Task 9(InactivityAlertRunner)가 사용한다.

- [ ] **Step 1: 인덱스 마이그레이션 작성**

`bali-infra/src/main/resources/db/migration/V18__add_sessions_date_status_index.sql`:

```sql
-- RoutineReminderRunner가 날짜+상태로 전체 유저의 세션을 스캔하는 배치 쿼리를 위한 인덱스
CREATE INDEX idx_sessions_date_status ON sessions(date, status);
```

- [ ] **Step 2: 포트에 메서드 추가**

`bali-core/src/main/kotlin/com/bali/core/session/WorkoutSessionRepository.kt`의 `interface WorkoutSessionRepository` 마지막 메서드(`findActiveDates`) 다음에 추가:

```kotlin

    // 특정 날짜/상태의 세션 전체를 유저 무관하게 조회 (배치의 리마인더 대상 조회용)
    fun findAllByDateAndStatus(date: LocalDate, status: SessionStatus): List<WorkoutSession>

    // 완료된 로그가 있는 가장 최근 날짜 (없으면 null). 이탈 알림 판정에 사용
    fun findLastActiveDate(userId: UUID): LocalDate?
```

- [ ] **Step 3: 실패하는 테스트 작성**

`bali-infra/src/test/kotlin/com/bali/infra/session/WorkoutSessionRepositoryAdapterTest.kt` 파일 끝(마지막 `}` 앞)에 추가:

```kotlin

    @Test
    fun `findAllByDateAndStatus는 유저 무관하게 날짜+상태가 일치하는 세션을 반환한다`() {
        val date = LocalDate.now()
        val matching = adapter.save(WorkoutSession(id = null, userId = UUID.randomUUID(), date = date, templateId = null, status = SessionStatus.SCHEDULED, logs = emptyList()))
        adapter.save(WorkoutSession(id = null, userId = UUID.randomUUID(), date = date, templateId = null, status = SessionStatus.COMPLETED, logs = emptyList()))
        adapter.save(WorkoutSession(id = null, userId = UUID.randomUUID(), date = date.minusDays(1), templateId = null, status = SessionStatus.SCHEDULED, logs = emptyList()))

        val result = adapter.findAllByDateAndStatus(date, SessionStatus.SCHEDULED)

        assertTrue(result.any { it.id == matching.id })
        assertTrue(result.all { it.date == date && it.status == SessionStatus.SCHEDULED })
    }

    @Test
    fun `findLastActiveDate는 완료된 로그가 있는 가장 최근 날짜를 반환한다`() {
        val userId = UUID.randomUUID()
        val exerciseId = UUID.randomUUID()
        val oldLog = SessionLog.create(ExerciseType.STRENGTH, exerciseId, sortOrder = 0, targetSets = 3, targetReps = 10, targetWeight = BigDecimal("40.0")).copy(completed = true)
        val recentLog = SessionLog.create(ExerciseType.STRENGTH, exerciseId, sortOrder = 0, targetSets = 3, targetReps = 10, targetWeight = BigDecimal("40.0")).copy(completed = true)
        adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.now().minusDays(10), templateId = null, status = SessionStatus.COMPLETED, logs = listOf(oldLog)))
        adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.now().minusDays(2), templateId = null, status = SessionStatus.COMPLETED, logs = listOf(recentLog)))

        assertEquals(LocalDate.now().minusDays(2), adapter.findLastActiveDate(userId))
    }

    @Test
    fun `완료된 로그가 없으면 findLastActiveDate는 null을 반환한다`() {
        assertEquals(null, adapter.findLastActiveDate(UUID.randomUUID()))
    }
```

- [ ] **Step 4: 테스트가 실패하는지 확인**

Run: `./gradlew :bali-infra:test --tests "com.bali.infra.session.WorkoutSessionRepositoryAdapterTest"`
Expected: FAIL — 컴파일 에러 (`findAllByDateAndStatus`/`findLastActiveDate` 없음)

- [ ] **Step 5: JPA 리포지토리/어댑터에 구현 추가**

`bali-infra/src/main/kotlin/com/bali/infra/session/WorkoutSessionJpaRepository.kt`의 `interface WorkoutSessionJpaRepository` 마지막 메서드 다음에 추가:

```kotlin

    // 특정 날짜/상태의 세션 전체 (유저 무관)
    @Query("SELECT s FROM WorkoutSessionJpaEntity s WHERE s.date = :date AND s.status = :status")
    fun findAllByDateAndStatus(@Param("date") date: LocalDate, @Param("status") status: SessionStatus): List<WorkoutSessionJpaEntity>

    // 완료된 로그가 있는 가장 최근 날짜
    @Query("""
        SELECT MAX(s.date) FROM WorkoutSessionJpaEntity s
        JOIN SessionLogJpaEntity l ON l.sessionId = s.id
        WHERE s.userId = :userId AND l.completed = true
    """)
    fun findLastActiveDate(@Param("userId") userId: UUID): LocalDate?
```

`WorkoutSessionJpaRepository.kt` 상단 import에 `import com.bali.core.session.SessionStatus` 추가 (아직 없다면).

`bali-infra/src/main/kotlin/com/bali/infra/session/WorkoutSessionRepositoryAdapter.kt`의 `findActiveDates` 구현 다음에 추가:

```kotlin

    // 특정 날짜/상태의 세션을 유저 무관하게 logs와 함께 조회
    @Transactional
    override fun findAllByDateAndStatus(date: LocalDate, status: SessionStatus): List<WorkoutSession> {
        val sessions = sessionJpaRepository.findAllByDateAndStatus(date, status)
        val logsBySessionId = logJpaRepository.findBySessionIdInOrderBySortOrderAsc(sessions.map { it.id }).groupBy { it.sessionId }
        return sessions.map { it.toDomain(logsBySessionId[it.id] ?: emptyList()) }
    }

    // 완료된 로그가 있는 가장 최근 날짜
    override fun findLastActiveDate(userId: UUID): LocalDate? =
        sessionJpaRepository.findLastActiveDate(userId)
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew :bali-infra:test --tests "com.bali.infra.session.WorkoutSessionRepositoryAdapterTest"`
Expected: PASS (전체)

- [ ] **Step 7: 커밋**

```bash
git add bali-infra/src/main/resources/db/migration/V18__add_sessions_date_status_index.sql \
  bali-core/src/main/kotlin/com/bali/core/session/WorkoutSessionRepository.kt \
  bali-infra/src/main/kotlin/com/bali/infra/session/WorkoutSessionJpaRepository.kt \
  bali-infra/src/main/kotlin/com/bali/infra/session/WorkoutSessionRepositoryAdapter.kt \
  bali-infra/src/test/kotlin/com/bali/infra/session/WorkoutSessionRepositoryAdapterTest.kt
git commit -m "feat(session): 리마인더/이탈 알림용 세션 조회 쿼리 추가"
```

---

## Task 7: NotificationDispatcher (bali-batch 공용 발송 헬퍼)

**Files:**
- Create: `bali-batch/src/main/kotlin/com/bali/batch/notification/NotificationDispatcher.kt`
- Create: `bali-batch/src/test/kotlin/com/bali/batch/notification/FakeNotificationSenderConfig.kt`
- Test: `bali-batch/src/test/kotlin/com/bali/batch/notification/NotificationDispatcherTest.kt`

**Interfaces:**
- Consumes: `DeviceTokenRepository`(Task 1), `NotificationLogRepository`(Task 3), `NotificationSender`(Task 4)
- Produces: `NotificationDispatcher.dispatch(userId: UUID, type: NotificationType, referenceId: UUID?, title: String, body: String): Unit` — Task 8~11의 모든 Runner가 이 메서드로 실제 발송을 수행한다.

- [ ] **Step 1: 테스트용 fake NotificationSender 작성**

`bali-batch/src/test/kotlin/com/bali/batch/notification/FakeNotificationSenderConfig.kt`:

```kotlin
package com.bali.batch.notification

import com.bali.core.notification.NotificationSender
import com.bali.core.notification.PushSendResult
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

// 배치 테스트에서 실제 Expo API를 호출하지 않도록 대체하는 fake. 모든 토큰을 발송 성공으로 처리한다
@TestConfiguration
class FakeNotificationSenderConfig {
    @Bean
    @Primary
    fun notificationSender(): NotificationSender = NotificationSender { messages ->
        messages.map { PushSendResult(token = it.token, ticketId = "fake-ticket-${it.token}", error = null) }
    }
}
```

- [ ] **Step 2: 실패하는 테스트 작성**

`bali-batch/src/test/kotlin/com/bali/batch/notification/NotificationDispatcherTest.kt`:

```kotlin
package com.bali.batch.notification

import com.bali.batch.BaliBatchApplication
import com.bali.core.notification.DevicePlatform
import com.bali.core.notification.DeviceToken
import com.bali.core.notification.DeviceTokenRepository
import com.bali.core.notification.NotificationLogRepository
import com.bali.core.notification.NotificationType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@SpringBootTest(classes = [BaliBatchApplication::class])
@Import(FakeNotificationSenderConfig::class)
@Transactional
class NotificationDispatcherTest {

    @Autowired lateinit var dispatcher: NotificationDispatcher
    @Autowired lateinit var deviceTokenRepository: DeviceTokenRepository
    @Autowired lateinit var notificationLogRepository: NotificationLogRepository

    @Test
    fun `등록된 토큰이 있으면 발송하고 notification_log에 기록한다`() {
        val userId = UUID.randomUUID()
        deviceTokenRepository.upsert(DeviceToken(id = null, userId = userId, expoPushToken = "ExponentPushToken[disp-${System.nanoTime()}]", platform = DevicePlatform.IOS, createdAt = Instant.now(), updatedAt = Instant.now()))
        val referenceId = UUID.randomUUID()

        dispatcher.dispatch(userId, NotificationType.ROUTINE_REMINDER, referenceId, "제목", "본문")

        assertTrue(notificationLogRepository.existsByTypeAndReferenceId(NotificationType.ROUTINE_REMINDER, referenceId))
    }

    @Test
    fun `등록된 토큰이 없으면 아무 것도 하지 않는다`() {
        val userId = UUID.randomUUID()
        val referenceId = UUID.randomUUID()

        dispatcher.dispatch(userId, NotificationType.ROUTINE_REMINDER, referenceId, "제목", "본문")

        assertFalse(notificationLogRepository.existsByTypeAndReferenceId(NotificationType.ROUTINE_REMINDER, referenceId))
    }
}
```

- [ ] **Step 3: 테스트가 실패하는지 확인**

Run: `./gradlew :bali-batch:test --tests "com.bali.batch.notification.NotificationDispatcherTest"`
Expected: FAIL — 컴파일 에러 (`NotificationDispatcher` 없음)

- [ ] **Step 4: NotificationDispatcher 구현**

`bali-batch/src/main/kotlin/com/bali/batch/notification/NotificationDispatcher.kt`:

```kotlin
package com.bali.batch.notification

import com.bali.core.notification.DeliveryStatus
import com.bali.core.notification.DeviceTokenRepository
import com.bali.core.notification.NotificationLog
import com.bali.core.notification.NotificationLogRepository
import com.bali.core.notification.NotificationSender
import com.bali.core.notification.NotificationType
import com.bali.core.notification.PushMessage
import com.bali.core.notification.PushSendError
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

// 유저 1명에게 알림을 발송하고 결과를 처리하는 공용 헬퍼. 모든 XxxRunner가 공유한다.
// DeviceNotRegistered 토큰은 즉시 삭제하고, 발송 성공(티켓 접수) 시에만 notification_log에 기록한다.
// 네트워크 등 예외는 그대로 던진다 - 로그를 안 남겨야 Airflow 재시도 시 자연스럽게 재발송된다
@Component
class NotificationDispatcher(
    private val deviceTokenRepository: DeviceTokenRepository,
    private val notificationLogRepository: NotificationLogRepository,
    private val notificationSender: NotificationSender,
) {
    fun dispatch(userId: UUID, type: NotificationType, referenceId: UUID?, title: String, body: String) {
        val tokens = deviceTokenRepository.findAllByUserId(userId)
        if (tokens.isEmpty()) return

        val results = notificationSender.send(tokens.map { PushMessage(token = it.expoPushToken, title = title, body = body) })

        results.filter { it.error == PushSendError.DEVICE_NOT_REGISTERED }
            .forEach { deviceTokenRepository.deleteByToken(it.token) }

        val firstSuccess = results.firstOrNull { it.ticketId != null } ?: return
        notificationLogRepository.save(
            NotificationLog(
                id = null, userId = userId, type = type, referenceId = referenceId,
                expoTicketId = firstSuccess.ticketId, deliveryStatus = DeliveryStatus.PENDING,
                deliveryError = null, sentAt = Instant.now(),
            )
        )
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :bali-batch:test --tests "com.bali.batch.notification.NotificationDispatcherTest"`
Expected: PASS (2 tests)

- [ ] **Step 6: DeviceNotRegistered 처리 테스트 추가 (별도 fake sender)**

`bali-batch/src/test/kotlin/com/bali/batch/notification/NotificationDispatcherDeviceNotRegisteredTest.kt`:

```kotlin
package com.bali.batch.notification

import com.bali.batch.BaliBatchApplication
import com.bali.core.notification.DevicePlatform
import com.bali.core.notification.DeviceToken
import com.bali.core.notification.DeviceTokenRepository
import com.bali.core.notification.NotificationLogRepository
import com.bali.core.notification.NotificationSender
import com.bali.core.notification.NotificationType
import com.bali.core.notification.PushSendError
import com.bali.core.notification.PushSendResult
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@TestConfiguration
class FailingNotificationSenderConfig {
    @Bean
    @Primary
    fun notificationSender(): NotificationSender = NotificationSender { messages ->
        messages.map { PushSendResult(token = it.token, ticketId = null, error = PushSendError.DEVICE_NOT_REGISTERED) }
    }
}

@SpringBootTest(classes = [BaliBatchApplication::class])
@Import(FailingNotificationSenderConfig::class)
@Transactional
class NotificationDispatcherDeviceNotRegisteredTest {

    @Autowired lateinit var dispatcher: NotificationDispatcher
    @Autowired lateinit var deviceTokenRepository: DeviceTokenRepository
    @Autowired lateinit var notificationLogRepository: NotificationLogRepository

    @Test
    fun `DeviceNotRegistered 에러 토큰은 삭제되고 로그는 남기지 않는다`() {
        val userId = UUID.randomUUID()
        deviceTokenRepository.upsert(DeviceToken(id = null, userId = userId, expoPushToken = "ExponentPushToken[dead-${System.nanoTime()}]", platform = DevicePlatform.IOS, createdAt = Instant.now(), updatedAt = Instant.now()))
        val referenceId = UUID.randomUUID()

        dispatcher.dispatch(userId, NotificationType.ROUTINE_REMINDER, referenceId, "제목", "본문")

        assertFalse(notificationLogRepository.existsByTypeAndReferenceId(NotificationType.ROUTINE_REMINDER, referenceId))
        assertTrue(deviceTokenRepository.findAllByUserId(userId).isEmpty())
    }
}
```

Run: `./gradlew :bali-batch:test --tests "com.bali.batch.notification.NotificationDispatcherDeviceNotRegisteredTest"`
Expected: PASS (1 test)

- [ ] **Step 7: 커밋**

```bash
git add bali-batch/src/main/kotlin/com/bali/batch/notification/NotificationDispatcher.kt \
  bali-batch/src/test/kotlin/com/bali/batch/notification/
git commit -m "feat(notification): 배치 공용 알림 발송 헬퍼(NotificationDispatcher) 구현"
```

---

## Task 8: RoutineReminderRunner + Airflow DAG

**Files:**
- Modify: `bali-batch/src/main/kotlin/com/bali/batch/BaliBatchApplication.kt`
- Create: `bali-batch/src/main/kotlin/com/bali/batch/notification/RoutineReminderRunner.kt`
- Create: `airflow/dags/routine_reminder_dag.py`
- Test: `bali-batch/src/test/kotlin/com/bali/batch/notification/RoutineReminderRunnerTest.kt`

**Interfaces:**
- Consumes: `WorkoutSessionRepository.findAllByDateAndStatus`(Task 6), `NotificationSettingsRepository`(Task 2), `NotificationLogRepository.existsByTypeAndReferenceId`(Task 3), `NotificationDispatcher.dispatch`(Task 7)
- Produces: `RoutineReminderRunner.run(): Int`, job key `"routine-reminder"`

- [ ] **Step 1: 실패하는 테스트 작성**

`bali-batch/src/test/kotlin/com/bali/batch/notification/RoutineReminderRunnerTest.kt`:

```kotlin
package com.bali.batch.notification

import com.bali.batch.BaliBatchApplication
import com.bali.core.notification.DevicePlatform
import com.bali.core.notification.DeviceToken
import com.bali.core.notification.DeviceTokenRepository
import com.bali.core.notification.NotificationLogRepository
import com.bali.core.notification.NotificationSender
import com.bali.core.notification.NotificationSettings
import com.bali.core.notification.NotificationSettingsRepository
import com.bali.core.notification.NotificationType
import com.bali.core.notification.PushMessage
import com.bali.core.notification.PushSendResult
import com.bali.core.session.SessionStatus
import com.bali.core.session.WorkoutSession
import com.bali.core.session.WorkoutSessionRepository
import com.bali.core.user.AuthProvider
import com.bali.core.user.User
import com.bali.core.user.UserRepository
import com.bali.core.user.UserStatus
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate

@SpringBootTest(classes = [BaliBatchApplication::class])
@Transactional
class RoutineReminderRunnerTest {

    @Autowired lateinit var runner: RoutineReminderRunner
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var sessionRepository: WorkoutSessionRepository
    @Autowired lateinit var deviceTokenRepository: DeviceTokenRepository
    @Autowired lateinit var settingsRepository: NotificationSettingsRepository
    @Autowired lateinit var notificationLogRepository: NotificationLogRepository
    @MockBean lateinit var notificationSender: NotificationSender

    private fun newUser() = userRepository.save(
        User(id = null, email = "reminder-${System.nanoTime()}@example.com", provider = AuthProvider.GOOGLE, providerId = "sub-reminder-${System.nanoTime()}", status = UserStatus.ACTIVE, createdAt = Instant.now())
    )

    private fun stubSuccessfulSend() {
        `when`(notificationSender.send(org.mockito.ArgumentMatchers.anyList())).thenAnswer { invocation ->
            val messages = invocation.getArgument<List<PushMessage>>(0)
            messages.map { PushSendResult(token = it.token, ticketId = "fake-ticket-${it.token}", error = null) }
        }
    }

    @Test
    fun `오늘 SCHEDULED 세션이 있고 토큰이 등록된 유저에게 발송하고 로그를 남긴다`() {
        stubSuccessfulSend()
        val user = newUser()
        deviceTokenRepository.upsert(DeviceToken(id = null, userId = user.id!!, expoPushToken = "ExponentPushToken[rr-${System.nanoTime()}]", platform = DevicePlatform.IOS, createdAt = Instant.now(), updatedAt = Instant.now()))
        val session = sessionRepository.save(WorkoutSession(id = null, userId = user.id!!, date = LocalDate.now(), templateId = null, status = SessionStatus.SCHEDULED, logs = emptyList()))

        val exitCode = runner.run()

        assertTrue(notificationLogRepository.existsByTypeAndReferenceId(NotificationType.ROUTINE_REMINDER, session.id!!))
        assertTrue(exitCode == 0)
    }

    @Test
    fun `이미 발송한 세션은 다시 발송하지 않는다`() {
        stubSuccessfulSend()
        val user = newUser()
        deviceTokenRepository.upsert(DeviceToken(id = null, userId = user.id!!, expoPushToken = "ExponentPushToken[rr2-${System.nanoTime()}]", platform = DevicePlatform.IOS, createdAt = Instant.now(), updatedAt = Instant.now()))
        val session = sessionRepository.save(WorkoutSession(id = null, userId = user.id!!, date = LocalDate.now(), templateId = null, status = SessionStatus.SCHEDULED, logs = emptyList()))

        runner.run()
        val firstLogCount = notificationLogRepository.existsByTypeAndReferenceId(NotificationType.ROUTINE_REMINDER, session.id!!)
        runner.run()

        assertTrue(firstLogCount)
        assertTrue(notificationLogRepository.existsByTypeAndReferenceId(NotificationType.ROUTINE_REMINDER, session.id!!))
    }

    @Test
    fun `routine_reminder_enabled가 false인 유저에게는 발송하지 않는다`() {
        val user = newUser()
        deviceTokenRepository.upsert(DeviceToken(id = null, userId = user.id!!, expoPushToken = "ExponentPushToken[rr3-${System.nanoTime()}]", platform = DevicePlatform.IOS, createdAt = Instant.now(), updatedAt = Instant.now()))
        settingsRepository.save(NotificationSettings(userId = user.id!!, routineReminderEnabled = false))
        val session = sessionRepository.save(WorkoutSession(id = null, userId = user.id!!, date = LocalDate.now(), templateId = null, status = SessionStatus.SCHEDULED, logs = emptyList()))

        runner.run()

        assertTrue(!notificationLogRepository.existsByTypeAndReferenceId(NotificationType.ROUTINE_REMINDER, session.id!!))
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew :bali-batch:test --tests "com.bali.batch.notification.RoutineReminderRunnerTest"`
Expected: FAIL — 컴파일 에러 (`RoutineReminderRunner` 없음)

- [ ] **Step 3: RoutineReminderRunner 구현**

`bali-batch/src/main/kotlin/com/bali/batch/notification/RoutineReminderRunner.kt`:

```kotlin
package com.bali.batch.notification

import com.bali.core.notification.NotificationLogRepository
import com.bali.core.notification.NotificationSettingsRepository
import com.bali.core.notification.NotificationType
import com.bali.core.session.SessionStatus
import com.bali.core.session.WorkoutSession
import com.bali.core.session.WorkoutSessionRepository
import com.bali.core.user.UserRepository
import com.bali.core.user.UserStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId

// 하루를 오전(00~12시)/오후(12~24시) 절반 지점(06시, 18시 KST)에 실행되어, 그날 날짜로 아직 시작 안 한
// SCHEDULED 세션이 있는 유저에게 리마인더를 보낸다. sessions 테이블에 시각 컬럼이 없어 날짜 단위로만 판정한다
@Component
class RoutineReminderRunner(
    private val sessionRepository: WorkoutSessionRepository,
    private val userRepository: UserRepository,
    private val settingsRepository: NotificationSettingsRepository,
    private val notificationLogRepository: NotificationLogRepository,
    private val dispatcher: NotificationDispatcher,
) {
    companion object {
        private val APP_ZONE = ZoneId.of("Asia/Seoul")
        private const val TITLE = "운동할 시간이에요"
        private const val BODY = "오늘 예약해둔 루틴이 아직 시작 전이에요"
    }

    private val log = LoggerFactory.getLogger(RoutineReminderRunner::class.java)

    fun run(): Int {
        val today = LocalDate.now(APP_ZONE)
        val sessions = sessionRepository.findAllByDateAndStatus(today, SessionStatus.SCHEDULED)
        var hadFailure = false

        sessions.forEach { session ->
            try {
                processSession(session)
            } catch (e: Exception) {
                log.error("루틴 리마인더 발송 실패: sessionId=${session.id}, userId=${session.userId}", e)
                hadFailure = true
            }
        }
        return if (hadFailure) 1 else 0
    }

    private fun processSession(session: WorkoutSession) {
        val sessionId = session.id!!
        if (notificationLogRepository.existsByTypeAndReferenceId(NotificationType.ROUTINE_REMINDER, sessionId)) return

        val user = userRepository.findById(session.userId) ?: return
        if (user.status != UserStatus.ACTIVE) return

        val settings = settingsRepository.findByUserId(user.id!!)
        if (settings?.routineReminderEnabled == false) return

        dispatcher.dispatch(user.id, NotificationType.ROUTINE_REMINDER, sessionId, TITLE, BODY)
    }
}
```

- [ ] **Step 4: `BaliBatchApplication`의 job dispatch에 추가**

`bali-batch/src/main/kotlin/com/bali/batch/BaliBatchApplication.kt`의 `when (job)` 블록에 `"monthly"` 줄 다음으로 추가:

```kotlin
        "routine-reminder" -> context.getBean<com.bali.batch.notification.RoutineReminderRunner>().run()
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :bali-batch:test --tests "com.bali.batch.notification.RoutineReminderRunnerTest"`
Expected: PASS (3 tests)

- [ ] **Step 6: Airflow DAG 작성**

`airflow/dags/routine_reminder_dag.py`:

```python
from datetime import timedelta

from airflow.sdk import DAG
from airflow.providers.docker.operators.docker import DockerOperator

# 매일 06시/18시(KST) = 21시/09시(UTC)에 bali-batch:local 컨테이너를 띄워 RoutineReminderRunner를 실행한다.
# 오전(00~12시)/오후(12~24시) 절반 지점마다 그날 아직 시작 안 한 SCHEDULED 세션이 있으면 리마인더를 보낸다.
with DAG(
    dag_id="routine_reminder",
    schedule="0 21,9 * * *",
    start_date=None,
    catchup=False,
    default_args={
        "retries": 1,
        "retry_delay": timedelta(minutes=5),
    },
) as dag:
    run_routine_reminder = DockerOperator(
        task_id="run_routine_reminder",
        image="bali-batch:local",
        command="routine-reminder",
        force_pull=False,
        auto_remove="success",
        network_mode="bali_default",
        environment={
            "SPRING_DATASOURCE_URL": "jdbc:postgresql://postgres:5432/bali",
        },
    )
```

- [ ] **Step 7: 커밋**

```bash
git add bali-batch/src/main/kotlin/com/bali/batch/BaliBatchApplication.kt \
  bali-batch/src/main/kotlin/com/bali/batch/notification/RoutineReminderRunner.kt \
  bali-batch/src/test/kotlin/com/bali/batch/notification/RoutineReminderRunnerTest.kt \
  airflow/dags/routine_reminder_dag.py
git commit -m "feat(notification): 루틴 예약 리마인더 배치+DAG 추가"
```

---

## Task 9: InactivityAlertRunner + Airflow DAG

**Files:**
- Modify: `bali-batch/src/main/kotlin/com/bali/batch/BaliBatchApplication.kt`
- Create: `bali-batch/src/main/kotlin/com/bali/batch/notification/InactivityAlertRunner.kt`
- Create: `airflow/dags/inactivity_alert_dag.py`
- Test: `bali-batch/src/test/kotlin/com/bali/batch/notification/InactivityAlertRunnerTest.kt`

**Interfaces:**
- Consumes: `WorkoutSessionRepository.findLastActiveDate`(Task 6), `NotificationSettingsRepository`(Task 2), `NotificationLogRepository.existsByUserIdAndTypeSentAfter`(Task 3), `NotificationDispatcher.dispatch`(Task 7)
- Produces: `InactivityAlertRunner.run(): Int`, job key `"inactivity-alert"`

- [ ] **Step 1: 실패하는 테스트 작성**

`bali-batch/src/test/kotlin/com/bali/batch/notification/InactivityAlertRunnerTest.kt`:

```kotlin
package com.bali.batch.notification

import com.bali.batch.BaliBatchApplication
import com.bali.core.exercise.ExerciseType
import com.bali.core.notification.DevicePlatform
import com.bali.core.notification.DeviceToken
import com.bali.core.notification.DeviceTokenRepository
import com.bali.core.notification.NotificationLogRepository
import com.bali.core.notification.NotificationSender
import com.bali.core.notification.NotificationType
import com.bali.core.notification.PushMessage
import com.bali.core.notification.PushSendResult
import com.bali.core.session.SessionLog
import com.bali.core.session.SessionStatus
import com.bali.core.session.WorkoutSession
import com.bali.core.session.WorkoutSessionRepository
import com.bali.core.user.AuthProvider
import com.bali.core.user.User
import com.bali.core.user.UserRepository
import com.bali.core.user.UserStatus
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@SpringBootTest(classes = [BaliBatchApplication::class])
@Transactional
class InactivityAlertRunnerTest {

    @Autowired lateinit var runner: InactivityAlertRunner
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var sessionRepository: WorkoutSessionRepository
    @Autowired lateinit var deviceTokenRepository: DeviceTokenRepository
    @Autowired lateinit var notificationLogRepository: NotificationLogRepository
    @MockBean lateinit var notificationSender: NotificationSender

    private fun newUser() = userRepository.save(
        User(id = null, email = "inactivity-${System.nanoTime()}@example.com", provider = AuthProvider.GOOGLE, providerId = "sub-inactivity-${System.nanoTime()}", status = UserStatus.ACTIVE, createdAt = Instant.now())
    )

    private fun completedSessionOn(userId: UUID, date: LocalDate) {
        val log = SessionLog.create(ExerciseType.STRENGTH, UUID.randomUUID(), sortOrder = 0, targetSets = 3, targetReps = 10, targetWeight = BigDecimal("40.0")).copy(completed = true)
        sessionRepository.save(WorkoutSession(id = null, userId = userId, date = date, templateId = null, status = SessionStatus.COMPLETED, logs = listOf(log)))
    }

    private fun stubSuccessfulSend() {
        `when`(notificationSender.send(org.mockito.ArgumentMatchers.anyList())).thenAnswer { invocation ->
            val messages = invocation.getArgument<List<PushMessage>>(0)
            messages.map { PushSendResult(token = it.token, ticketId = "fake-ticket-${it.token}", error = null) }
        }
    }

    @Test
    fun `마지막 운동이 7일 이상 지난 유저에게 발송한다`() {
        stubSuccessfulSend()
        val user = newUser()
        deviceTokenRepository.upsert(DeviceToken(id = null, userId = user.id!!, expoPushToken = "ExponentPushToken[ia-${System.nanoTime()}]", platform = DevicePlatform.IOS, createdAt = Instant.now(), updatedAt = Instant.now()))
        completedSessionOn(user.id, LocalDate.now().minusDays(8))

        runner.run()

        assertTrue(notificationLogRepository.existsByUserIdAndTypeSentAfter(user.id, NotificationType.INACTIVITY_ALERT, Instant.now().minusSeconds(60)))
    }

    @Test
    fun `마지막 운동이 7일 미만이면 발송하지 않는다`() {
        val user = newUser()
        deviceTokenRepository.upsert(DeviceToken(id = null, userId = user.id!!, expoPushToken = "ExponentPushToken[ia2-${System.nanoTime()}]", platform = DevicePlatform.IOS, createdAt = Instant.now(), updatedAt = Instant.now()))
        completedSessionOn(user.id, LocalDate.now().minusDays(3))

        runner.run()

        assertFalse(notificationLogRepository.existsByUserIdAndTypeSentAfter(user.id, NotificationType.INACTIVITY_ALERT, Instant.now().minusSeconds(60)))
    }

    @Test
    fun `운동 기록이 아예 없는 유저에게는 발송하지 않는다`() {
        val user = newUser()
        deviceTokenRepository.upsert(DeviceToken(id = null, userId = user.id!!, expoPushToken = "ExponentPushToken[ia3-${System.nanoTime()}]", platform = DevicePlatform.IOS, createdAt = Instant.now(), updatedAt = Instant.now()))

        runner.run()

        assertFalse(notificationLogRepository.existsByUserIdAndTypeSentAfter(user.id, NotificationType.INACTIVITY_ALERT, Instant.now().minusSeconds(60)))
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew :bali-batch:test --tests "com.bali.batch.notification.InactivityAlertRunnerTest"`
Expected: FAIL — 컴파일 에러

- [ ] **Step 3: InactivityAlertRunner 구현**

`bali-batch/src/main/kotlin/com/bali/batch/notification/InactivityAlertRunner.kt`:

```kotlin
package com.bali.batch.notification

import com.bali.core.notification.NotificationLogRepository
import com.bali.core.notification.NotificationSettingsRepository
import com.bali.core.notification.NotificationType
import com.bali.core.session.WorkoutSessionRepository
import com.bali.core.user.User
import com.bali.core.user.UserRepository
import com.bali.core.user.UserStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// 매일 1회 실행되어, 마지막으로 완료된 세션 로그가 7일 이상 지난 ACTIVE 유저에게 이탈 알림을 보낸다.
// 운동 기록이 아예 없는 유저는 대상에서 제외한다 (첫 운동 유도는 별도 온보딩 알림의 몫)
@Component
class InactivityAlertRunner(
    private val userRepository: UserRepository,
    private val sessionRepository: WorkoutSessionRepository,
    private val settingsRepository: NotificationSettingsRepository,
    private val notificationLogRepository: NotificationLogRepository,
    private val dispatcher: NotificationDispatcher,
) {
    companion object {
        private val APP_ZONE = ZoneId.of("Asia/Seoul")
        private const val INACTIVITY_THRESHOLD_DAYS = 7L
        private const val TITLE = "오랜만이에요"
        private const val BODY = "7일째 운동 기록이 없어요. 오늘 가볍게 시작해볼까요?"
    }

    private val log = LoggerFactory.getLogger(InactivityAlertRunner::class.java)

    fun run(): Int {
        val today = LocalDate.now(APP_ZONE)
        var hadFailure = false

        userRepository.findAllByStatus(UserStatus.ACTIVE).forEach { user ->
            try {
                processUser(user, today)
            } catch (e: Exception) {
                log.error("이탈 알림 발송 실패: userId=${user.id}", e)
                hadFailure = true
            }
        }
        return if (hadFailure) 1 else 0
    }

    private fun processUser(user: User, today: LocalDate) {
        val settings = settingsRepository.findByUserId(user.id!!)
        if (settings?.inactivityAlertEnabled == false) return

        val lastActiveDate = sessionRepository.findLastActiveDate(user.id) ?: return
        if (ChronoUnit.DAYS.between(lastActiveDate, today) < INACTIVITY_THRESHOLD_DAYS) return

        val cooldownStart = Instant.now().minus(INACTIVITY_THRESHOLD_DAYS, ChronoUnit.DAYS)
        if (notificationLogRepository.existsByUserIdAndTypeSentAfter(user.id, NotificationType.INACTIVITY_ALERT, cooldownStart)) return

        dispatcher.dispatch(user.id, NotificationType.INACTIVITY_ALERT, referenceId = null, TITLE, BODY)
    }
}
```

- [ ] **Step 4: `BaliBatchApplication`의 job dispatch에 추가**

`bali-batch/src/main/kotlin/com/bali/batch/BaliBatchApplication.kt`의 `when (job)` 블록에 추가:

```kotlin
        "inactivity-alert" -> context.getBean<com.bali.batch.notification.InactivityAlertRunner>().run()
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :bali-batch:test --tests "com.bali.batch.notification.InactivityAlertRunnerTest"`
Expected: PASS (3 tests)

- [ ] **Step 6: Airflow DAG 작성**

`airflow/dags/inactivity_alert_dag.py`:

```python
from datetime import timedelta

from airflow.sdk import DAG
from airflow.providers.docker.operators.docker import DockerOperator

# 매일 09시(KST) = 00시(UTC)에 bali-batch:local 컨테이너를 띄워 InactivityAlertRunner를 실행한다.
# 마지막 완료 세션이 7일 이상 지난 ACTIVE 유저에게 이탈 알림을 보낸다.
with DAG(
    dag_id="inactivity_alert",
    schedule="0 0 * * *",
    start_date=None,
    catchup=False,
    default_args={
        "retries": 1,
        "retry_delay": timedelta(minutes=5),
    },
) as dag:
    run_inactivity_alert = DockerOperator(
        task_id="run_inactivity_alert",
        image="bali-batch:local",
        command="inactivity-alert",
        force_pull=False,
        auto_remove="success",
        network_mode="bali_default",
        environment={
            "SPRING_DATASOURCE_URL": "jdbc:postgresql://postgres:5432/bali",
        },
    )
```

- [ ] **Step 7: 커밋**

```bash
git add bali-batch/src/main/kotlin/com/bali/batch/BaliBatchApplication.kt \
  bali-batch/src/main/kotlin/com/bali/batch/notification/InactivityAlertRunner.kt \
  bali-batch/src/test/kotlin/com/bali/batch/notification/InactivityAlertRunnerTest.kt \
  airflow/dags/inactivity_alert_dag.py
git commit -m "feat(notification): 운동 미실행/이탈 알림 배치+DAG 추가"
```

---

## Task 10: WeeklySummaryPushRunner + weekly_analysis DAG 확장

**Files:**
- Modify: `bali-batch/src/main/kotlin/com/bali/batch/BaliBatchApplication.kt`
- Create: `bali-batch/src/main/kotlin/com/bali/batch/notification/WeeklySummaryPushRunner.kt`
- Modify: `airflow/dags/weekly_analysis_dag.py`
- Test: `bali-batch/src/test/kotlin/com/bali/batch/notification/WeeklySummaryPushRunnerTest.kt`

**Interfaces:**
- Consumes: `WeeklyAnalysisRepository`(기존), `NotificationSettingsRepository`(Task 2), `NotificationLogRepository.existsByTypeAndReferenceId`(Task 3), `NotificationDispatcher.dispatch`(Task 7)
- Produces: `WeeklySummaryPushRunner.run(): Int`, job key `"weekly-summary-push"`

- [ ] **Step 1: 실패하는 테스트 작성**

`bali-batch/src/test/kotlin/com/bali/batch/notification/WeeklySummaryPushRunnerTest.kt`:

```kotlin
package com.bali.batch.notification

import com.bali.batch.BaliBatchApplication
import com.bali.core.analysis.AnalysisStatus
import com.bali.core.analysis.WeeklyAnalysis
import com.bali.core.analysis.WeeklyAnalysisRepository
import com.bali.core.notification.DevicePlatform
import com.bali.core.notification.DeviceToken
import com.bali.core.notification.DeviceTokenRepository
import com.bali.core.notification.NotificationLogRepository
import com.bali.core.notification.NotificationSender
import com.bali.core.notification.NotificationType
import com.bali.core.notification.PushMessage
import com.bali.core.notification.PushSendResult
import com.bali.core.user.AuthProvider
import com.bali.core.user.User
import com.bali.core.user.UserRepository
import com.bali.core.user.UserStatus
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@SpringBootTest(classes = [BaliBatchApplication::class])
@Transactional
class WeeklySummaryPushRunnerTest {

    @Autowired lateinit var runner: WeeklySummaryPushRunner
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var analysisRepository: WeeklyAnalysisRepository
    @Autowired lateinit var deviceTokenRepository: DeviceTokenRepository
    @Autowired lateinit var notificationLogRepository: NotificationLogRepository
    @MockBean lateinit var notificationSender: NotificationSender

    private fun newUser() = userRepository.save(
        User(id = null, email = "wsummary-${System.nanoTime()}@example.com", provider = AuthProvider.GOOGLE, providerId = "sub-wsummary-${System.nanoTime()}", status = UserStatus.ACTIVE, createdAt = Instant.now())
    )

    private fun lastCompletedWeekOf(): LocalDate =
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)

    private fun stubSuccessfulSend() {
        `when`(notificationSender.send(org.mockito.ArgumentMatchers.anyList())).thenAnswer { invocation ->
            val messages = invocation.getArgument<List<PushMessage>>(0)
            messages.map { PushSendResult(token = it.token, ticketId = "fake-ticket-${it.token}", error = null) }
        }
    }

    @Test
    fun `직전 주 분석이 SUCCESS인 유저에게 발송한다`() {
        stubSuccessfulSend()
        val user = newUser()
        deviceTokenRepository.upsert(DeviceToken(id = null, userId = user.id!!, expoPushToken = "ExponentPushToken[ws-${System.nanoTime()}]", platform = DevicePlatform.IOS, createdAt = Instant.now(), updatedAt = Instant.now()))
        val analysis = analysisRepository.save(WeeklyAnalysis(id = null, userId = user.id, weekOf = lastCompletedWeekOf(), status = AnalysisStatus.SUCCESS, summary = com.bali.core.analysis.AnalysisSummary(totalWorkoutMinutes = 60, volumeByExercise = emptyMap(), volumeByMuscleGroup = emptyMap(), cardioTotalMinutes = 0, completionRate = java.math.BigDecimal("1.0"), volumeChangeFromLastWeekPercent = null), insights = emptyList()))

        runner.run()

        assertTrue(notificationLogRepository.existsByTypeAndReferenceId(NotificationType.WEEKLY_SUMMARY, analysis.id!!))
    }

    @Test
    fun `직전 주 분석이 NO_ACTIVITY면 발송하지 않는다`() {
        val user = newUser()
        deviceTokenRepository.upsert(DeviceToken(id = null, userId = user.id!!, expoPushToken = "ExponentPushToken[ws2-${System.nanoTime()}]", platform = DevicePlatform.IOS, createdAt = Instant.now(), updatedAt = Instant.now()))
        val analysis = analysisRepository.save(WeeklyAnalysis(id = null, userId = user.id, weekOf = lastCompletedWeekOf(), status = AnalysisStatus.NO_ACTIVITY, summary = null, insights = emptyList()))

        runner.run()

        assertFalse(notificationLogRepository.existsByTypeAndReferenceId(NotificationType.WEEKLY_SUMMARY, analysis.id!!))
    }
}
```

(`AnalysisSummary`는 `bali-core/src/main/kotlin/com/bali/core/analysis/AnalysisSummary.kt`에 정의된 실제 필드 그대로다: `completionRate`/`volumeChangeFromLastWeekPercent`는 `BigDecimal`.)

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew :bali-batch:test --tests "com.bali.batch.notification.WeeklySummaryPushRunnerTest"`
Expected: FAIL — 컴파일 에러

- [ ] **Step 3: WeeklySummaryPushRunner 구현**

`bali-batch/src/main/kotlin/com/bali/batch/notification/WeeklySummaryPushRunner.kt`:

```kotlin
package com.bali.batch.notification

import com.bali.core.analysis.AnalysisStatus
import com.bali.core.analysis.WeeklyAnalysisRepository
import com.bali.core.notification.NotificationLogRepository
import com.bali.core.notification.NotificationSettingsRepository
import com.bali.core.notification.NotificationType
import com.bali.core.user.User
import com.bali.core.user.UserRepository
import com.bali.core.user.UserStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

// weekly_analysis 배치 직후 실행되어, 방금 SUCCESS로 집계된 직전 주 분석 결과를 유저에게 요약 알림으로 보낸다
@Component
class WeeklySummaryPushRunner(
    private val userRepository: UserRepository,
    private val analysisRepository: WeeklyAnalysisRepository,
    private val settingsRepository: NotificationSettingsRepository,
    private val notificationLogRepository: NotificationLogRepository,
    private val dispatcher: NotificationDispatcher,
) {
    companion object {
        private val APP_ZONE = ZoneId.of("Asia/Seoul")
        private const val TITLE = "이번 주 운동 요약이 도착했어요"
        private const val BODY = "이번 주 운동 기록을 확인해보세요"
    }

    private val log = LoggerFactory.getLogger(WeeklySummaryPushRunner::class.java)

    fun run(): Int {
        val weekOf = LocalDate.now(APP_ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
        var hadFailure = false

        userRepository.findAllByStatus(UserStatus.ACTIVE).forEach { user ->
            try {
                processUser(user, weekOf)
            } catch (e: Exception) {
                log.error("주간 요약 알림 발송 실패: userId=${user.id}, weekOf=$weekOf", e)
                hadFailure = true
            }
        }
        return if (hadFailure) 1 else 0
    }

    private fun processUser(user: User, weekOf: LocalDate) {
        val settings = settingsRepository.findByUserId(user.id!!)
        if (settings?.summaryNotificationEnabled == false) return

        val analysis = analysisRepository.findByUserIdAndWeekOf(user.id, weekOf) ?: return
        if (analysis.status != AnalysisStatus.SUCCESS) return

        val analysisId = analysis.id!!
        if (notificationLogRepository.existsByTypeAndReferenceId(NotificationType.WEEKLY_SUMMARY, analysisId)) return

        dispatcher.dispatch(user.id, NotificationType.WEEKLY_SUMMARY, analysisId, TITLE, BODY)
    }
}
```

- [ ] **Step 4: `BaliBatchApplication`의 job dispatch에 추가**

```kotlin
        "weekly-summary-push" -> context.getBean<com.bali.batch.notification.WeeklySummaryPushRunner>().run()
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :bali-batch:test --tests "com.bali.batch.notification.WeeklySummaryPushRunnerTest"`
Expected: PASS (2 tests)

- [ ] **Step 6: `weekly_analysis_dag.py`에 태스크 추가**

`airflow/dags/weekly_analysis_dag.py`를 다음으로 교체:

```python
from datetime import timedelta

from airflow.sdk import DAG
from airflow.providers.docker.operators.docker import DockerOperator

# 매주 월요일, bali-batch:local 컨테이너를 sibling으로 띄워 WeeklyAnalysisRunner를 실행하고,
# 성공하면 이어서 WeeklySummaryPushRunner로 방금 집계된 결과를 요약 알림으로 보낸다.
# exit code 0/1을 DockerOperator가 그대로 태스크 성공/실패로 반영한다.
with DAG(
    dag_id="weekly_analysis",
    schedule="0 0 * * 1",
    start_date=None,
    catchup=False,
    default_args={
        "retries": 1,
        "retry_delay": timedelta(minutes=5),
    },
) as dag:
    run_weekly_analysis = DockerOperator(
        task_id="run_weekly_analysis",
        image="bali-batch:local",
        command="weekly",
        force_pull=False,
        auto_remove="success",
        network_mode="bali_default",
        environment={
            "SPRING_DATASOURCE_URL": "jdbc:postgresql://postgres:5432/bali",
        },
    )

    send_weekly_summary_push = DockerOperator(
        task_id="send_weekly_summary_push",
        image="bali-batch:local",
        command="weekly-summary-push",
        force_pull=False,
        auto_remove="success",
        network_mode="bali_default",
        environment={
            "SPRING_DATASOURCE_URL": "jdbc:postgresql://postgres:5432/bali",
        },
    )

    run_weekly_analysis >> send_weekly_summary_push
```

(기존 `run_weekly_analysis`에 `command`가 없었는데, `BaliBatchApplication`의 dispatch 기본값이 `"weekly"`라 동작은 동일하다. 명시적으로 `command="weekly"`를 추가해 두 태스크의 의도를 나란히 읽히게 한다.)

- [ ] **Step 7: 커밋**

```bash
git add bali-batch/src/main/kotlin/com/bali/batch/BaliBatchApplication.kt \
  bali-batch/src/main/kotlin/com/bali/batch/notification/WeeklySummaryPushRunner.kt \
  bali-batch/src/test/kotlin/com/bali/batch/notification/WeeklySummaryPushRunnerTest.kt \
  airflow/dags/weekly_analysis_dag.py
git commit -m "feat(notification): 주간 통계 요약 알림 배치 추가 및 DAG에 연결"
```

---

## Task 11: MonthlySummaryPushRunner + monthly_analysis DAG 확장

**Files:**
- Modify: `bali-batch/src/main/kotlin/com/bali/batch/BaliBatchApplication.kt`
- Create: `bali-batch/src/main/kotlin/com/bali/batch/notification/MonthlySummaryPushRunner.kt`
- Modify: `airflow/dags/monthly_analysis_dag.py`
- Test: `bali-batch/src/test/kotlin/com/bali/batch/notification/MonthlySummaryPushRunnerTest.kt`

**Interfaces:**
- Consumes: `MonthlyAnalysisRepository`(bali-backend-d8 구현 완료, `com.bali.core.analysis.MonthlyAnalysisRepository`), `NotificationSettingsRepository`(Task 2), `NotificationLogRepository.existsByTypeAndReferenceId`(Task 3), `NotificationDispatcher.dispatch`(Task 7)
- Produces: `MonthlySummaryPushRunner.run(): Int`, job key `"monthly-summary-push"`

**참고:** `MonthlyAnalysis`/`MonthlyAnalysisRepository`는 이 스펙 논의 도중 별도 세션이 이미 구현했다. Task 10의 `WeeklySummaryPushRunner`를 월간으로 그대로 미러링하면 된다 (`monthOf` 계산만 다름).

- [ ] **Step 1: 실패하는 테스트 작성**

`bali-batch/src/test/kotlin/com/bali/batch/notification/MonthlySummaryPushRunnerTest.kt`:

```kotlin
package com.bali.batch.notification

import com.bali.batch.BaliBatchApplication
import com.bali.core.analysis.AnalysisStatus
import com.bali.core.analysis.MonthlyAnalysis
import com.bali.core.analysis.MonthlyAnalysisRepository
import com.bali.core.notification.DevicePlatform
import com.bali.core.notification.DeviceToken
import com.bali.core.notification.DeviceTokenRepository
import com.bali.core.notification.NotificationLogRepository
import com.bali.core.notification.NotificationSender
import com.bali.core.notification.NotificationType
import com.bali.core.notification.PushMessage
import com.bali.core.notification.PushSendResult
import com.bali.core.user.AuthProvider
import com.bali.core.user.User
import com.bali.core.user.UserRepository
import com.bali.core.user.UserStatus
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate

@SpringBootTest(classes = [BaliBatchApplication::class])
@Transactional
class MonthlySummaryPushRunnerTest {

    @Autowired lateinit var runner: MonthlySummaryPushRunner
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var analysisRepository: MonthlyAnalysisRepository
    @Autowired lateinit var deviceTokenRepository: DeviceTokenRepository
    @Autowired lateinit var notificationLogRepository: NotificationLogRepository
    @MockBean lateinit var notificationSender: NotificationSender

    private fun newUser() = userRepository.save(
        User(id = null, email = "msummary-${System.nanoTime()}@example.com", provider = AuthProvider.GOOGLE, providerId = "sub-msummary-${System.nanoTime()}", status = UserStatus.ACTIVE, createdAt = Instant.now())
    )

    private fun lastCompletedMonthOf(): LocalDate =
        LocalDate.now().withDayOfMonth(1).minusMonths(1)

    private fun stubSuccessfulSend() {
        `when`(notificationSender.send(org.mockito.ArgumentMatchers.anyList())).thenAnswer { invocation ->
            val messages = invocation.getArgument<List<PushMessage>>(0)
            messages.map { PushSendResult(token = it.token, ticketId = "fake-ticket-${it.token}", error = null) }
        }
    }

    @Test
    fun `직전 달 분석이 SUCCESS인 유저에게 발송한다`() {
        stubSuccessfulSend()
        val user = newUser()
        deviceTokenRepository.upsert(DeviceToken(id = null, userId = user.id!!, expoPushToken = "ExponentPushToken[ms-${System.nanoTime()}]", platform = DevicePlatform.IOS, createdAt = Instant.now(), updatedAt = Instant.now()))
        val analysis = analysisRepository.save(MonthlyAnalysis(id = null, userId = user.id, monthOf = lastCompletedMonthOf(), status = AnalysisStatus.SUCCESS, summary = com.bali.core.analysis.AnalysisSummary(totalWorkoutMinutes = 240, volumeByExercise = emptyMap(), volumeByMuscleGroup = emptyMap(), cardioTotalMinutes = 0, completionRate = java.math.BigDecimal("1.0"), volumeChangeFromLastWeekPercent = null), insights = emptyList()))

        runner.run()

        assertTrue(notificationLogRepository.existsByTypeAndReferenceId(NotificationType.MONTHLY_SUMMARY, analysis.id!!))
    }

    @Test
    fun `직전 달 분석이 NO_ACTIVITY면 발송하지 않는다`() {
        val user = newUser()
        deviceTokenRepository.upsert(DeviceToken(id = null, userId = user.id!!, expoPushToken = "ExponentPushToken[ms2-${System.nanoTime()}]", platform = DevicePlatform.IOS, createdAt = Instant.now(), updatedAt = Instant.now()))
        val analysis = analysisRepository.save(MonthlyAnalysis(id = null, userId = user.id, monthOf = lastCompletedMonthOf(), status = AnalysisStatus.NO_ACTIVITY, summary = null, insights = emptyList()))

        runner.run()

        assertFalse(notificationLogRepository.existsByTypeAndReferenceId(NotificationType.MONTHLY_SUMMARY, analysis.id!!))
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew :bali-batch:test --tests "com.bali.batch.notification.MonthlySummaryPushRunnerTest"`
Expected: FAIL — 컴파일 에러

- [ ] **Step 3: MonthlySummaryPushRunner 구현**

`bali-batch/src/main/kotlin/com/bali/batch/notification/MonthlySummaryPushRunner.kt`:

```kotlin
package com.bali.batch.notification

import com.bali.core.analysis.AnalysisStatus
import com.bali.core.analysis.MonthlyAnalysisRepository
import com.bali.core.notification.NotificationLogRepository
import com.bali.core.notification.NotificationSettingsRepository
import com.bali.core.notification.NotificationType
import com.bali.core.user.User
import com.bali.core.user.UserRepository
import com.bali.core.user.UserStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId

// monthly_analysis 배치 직후 실행되어, 방금 SUCCESS로 집계된 직전 달 분석 결과를 유저에게 요약 알림으로 보낸다
@Component
class MonthlySummaryPushRunner(
    private val userRepository: UserRepository,
    private val analysisRepository: MonthlyAnalysisRepository,
    private val settingsRepository: NotificationSettingsRepository,
    private val notificationLogRepository: NotificationLogRepository,
    private val dispatcher: NotificationDispatcher,
) {
    companion object {
        private val APP_ZONE = ZoneId.of("Asia/Seoul")
        private const val TITLE = "이번 달 운동 요약이 도착했어요"
        private const val BODY = "이번 달 운동 기록을 확인해보세요"
    }

    private val log = LoggerFactory.getLogger(MonthlySummaryPushRunner::class.java)

    fun run(): Int {
        val monthOf = LocalDate.now(APP_ZONE).withDayOfMonth(1).minusMonths(1)
        var hadFailure = false

        userRepository.findAllByStatus(UserStatus.ACTIVE).forEach { user ->
            try {
                processUser(user, monthOf)
            } catch (e: Exception) {
                log.error("월간 요약 알림 발송 실패: userId=${user.id}, monthOf=$monthOf", e)
                hadFailure = true
            }
        }
        return if (hadFailure) 1 else 0
    }

    private fun processUser(user: User, monthOf: LocalDate) {
        val settings = settingsRepository.findByUserId(user.id!!)
        if (settings?.summaryNotificationEnabled == false) return

        val analysis = analysisRepository.findByUserIdAndMonthOf(user.id, monthOf) ?: return
        if (analysis.status != AnalysisStatus.SUCCESS) return

        val analysisId = analysis.id!!
        if (notificationLogRepository.existsByTypeAndReferenceId(NotificationType.MONTHLY_SUMMARY, analysisId)) return

        dispatcher.dispatch(user.id, NotificationType.MONTHLY_SUMMARY, analysisId, TITLE, BODY)
    }
}
```

- [ ] **Step 4: `BaliBatchApplication`의 job dispatch에 추가**

```kotlin
        "monthly-summary-push" -> context.getBean<com.bali.batch.notification.MonthlySummaryPushRunner>().run()
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :bali-batch:test --tests "com.bali.batch.notification.MonthlySummaryPushRunnerTest"`
Expected: PASS (2 tests)

- [ ] **Step 6: `monthly_analysis_dag.py`에 태스크 추가**

`airflow/dags/monthly_analysis_dag.py`를 다음으로 교체:

```python
from datetime import timedelta

from airflow.sdk import DAG
from airflow.providers.docker.operators.docker import DockerOperator

# 매달 1일, bali-batch:local 컨테이너를 sibling으로 띄워 MonthlyAnalysisRunner를 실행하고,
# 성공하면 이어서 MonthlySummaryPushRunner로 방금 집계된 결과를 요약 알림으로 보낸다.
# exit code 0/1을 DockerOperator가 그대로 태스크 성공/실패로 반영한다.
with DAG(
    dag_id="monthly_analysis",
    schedule="0 0 1 * *",
    start_date=None,
    catchup=False,
    default_args={
        "retries": 1,
        "retry_delay": timedelta(minutes=5),
    },
) as dag:
    run_monthly_analysis = DockerOperator(
        task_id="run_monthly_analysis",
        image="bali-batch:local",
        command="monthly",
        force_pull=False,
        auto_remove="success",
        network_mode="bali_default",
        environment={
            "SPRING_DATASOURCE_URL": "jdbc:postgresql://postgres:5432/bali",
        },
    )

    send_monthly_summary_push = DockerOperator(
        task_id="send_monthly_summary_push",
        image="bali-batch:local",
        command="monthly-summary-push",
        force_pull=False,
        auto_remove="success",
        network_mode="bali_default",
        environment={
            "SPRING_DATASOURCE_URL": "jdbc:postgresql://postgres:5432/bali",
        },
    )

    run_monthly_analysis >> send_monthly_summary_push
```

- [ ] **Step 7: 커밋**

```bash
git add bali-batch/src/main/kotlin/com/bali/batch/BaliBatchApplication.kt \
  bali-batch/src/main/kotlin/com/bali/batch/notification/MonthlySummaryPushRunner.kt \
  bali-batch/src/test/kotlin/com/bali/batch/notification/MonthlySummaryPushRunnerTest.kt \
  airflow/dags/monthly_analysis_dag.py
git commit -m "feat(notification): 월간 통계 요약 알림 배치 추가 및 DAG에 연결"
```

---

## 전체 검증

모든 태스크 완료 후:

- [ ] **전체 테스트 실행**

Run: `./gradlew test`
Expected: 전체 PASS (bali-core는 순수 도메인이라 테스트 없음, bali-infra/bali-api/bali-batch 전체 통과)

- [ ] **bali-batch Docker 이미지 재빌드 (Airflow가 새 job key를 인식하도록)**

Run: `docker compose build bali-batch` 또는 `docker build -t bali-batch:local -f bali-batch/Dockerfile .`

- [ ] **bali-frontend에 새 엔드포인트 안내**

`POST/DELETE /api/v1/notifications/device-token`, `GET/PATCH /api/v1/notifications/settings` 4개 엔드포인트가 추가됐음을 bali-frontend 세션에 SendMessage로 알린다.
