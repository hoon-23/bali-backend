# 알림함 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (inline) to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 서버가 보낸 푸시를 최근 30일 목록으로 조회하고 안 읽은 개수/읽음 처리를 제공한다.

**Architecture:** 기존 `notification_log`에 `title/body/read_at`을 추가해 알림함으로 재사용한다. 디스패처가 발송 시 title/body를 저장하고 푸시 `data`에 type/referenceId를 싣는다. 조회/읽음 API는 신규 `NotificationInboxController`로 분리한다.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA, Flyway, JUnit5 + MockMvc, 로컬 Postgres(`docker compose`).

**Spec:** `docs/superpowers/specs/2026-10-01-notification-inbox-design.md`

## Global Constraints

- 커밋 메시지는 한국어, 접두사는 영어. 트레일러 없음. 브랜치 `develop`. 주석은 메서드당 한 줄(한국어).
- 조회 범위는 `sent_at` 기준 최근 30일. 삭제/숨기기 API와 `deleted_at`은 이번 범위가 아니다.
- 테스트는 로컬 Postgres 필요(`docker compose start`). Gradle은 프로젝트 루트에서 실행한다.
- 프론트 파일은 직접 수정하지 않고 SendMessage로만 전달한다.

## Review Focus

1. 다른 유저 알림 id로 PATCH하면 404이고 그 행은 읽음 처리되지 않는다. (Task 2)
2. 31일 전 알림은 목록과 안 읽은 개수에서 빠진다. (Task 1, 2)
3. 이미 읽은 알림을 다시 PATCH해도 최초 `read_at`이 유지된다. (Task 1)
4. `size` 0/음수/초과, `page` 음수 입력이 안전하게 보정된다. (Task 2)
5. 푸시 `data`에 `referenceId`가 null인 알림(INACTIVITY_ALERT)도 빈 문자열로 실려 발송이 깨지지 않는다. (Task 1)

---

### Task 1: 영속 계층 + 디스패처 (V27, 도메인, 어댑터, 디스패처)

**Files:**
- Create: `bali-infra/src/main/resources/db/migration/V27__add_inbox_columns_to_notification_log.sql`
- Modify: `bali-core/src/main/kotlin/com/bali/core/notification/NotificationLog.kt`, `NotificationLogRepository.kt`
- Modify: `bali-infra/src/main/kotlin/com/bali/infra/notification/NotificationLogJpaEntity.kt`, `NotificationLogJpaRepository.kt`, `NotificationLogRepositoryAdapter.kt`
- Modify: `bali-batch/src/main/kotlin/com/bali/batch/notification/NotificationDispatcher.kt`
- Test: `bali-infra/src/test/kotlin/com/bali/infra/notification/NotificationLogRepositoryAdapterTest.kt`, `bali-batch/src/test/kotlin/com/bali/batch/notification/NotificationDispatcherTest.kt`

**Interfaces:**
- Produces (Task 2가 사용):
  - `NotificationLog(..., sentAt: Instant, title: String = "", body: String = "", readAt: Instant? = null)`
  - `data class NotificationLogPage(val items: List<NotificationLog>, val totalElements: Long)` (core 패키지 `com.bali.core.notification`)
  - `fun findPageByUserIdSentAfter(userId: UUID, since: Instant, page: Int, size: Int): NotificationLogPage` (sentAt 내림차순)
  - `fun countUnreadByUserIdSentAfter(userId: UUID, since: Instant): Long`
  - `fun markRead(userId: UUID, id: UUID, at: Instant): Boolean` (본인 소유 아니거나 없으면 false, 이미 읽음이면 기존 값 유지하고 true)
  - `fun markAllRead(userId: UUID, at: Instant): Int` (본인 안 읽은 행 전부)

- [ ] **Step 1: 실패하는 테스트 작성**

`NotificationLogRepositoryAdapterTest` 클래스 끝(마지막 `}` 직전)에 추가. import에 `java.time.temporal.ChronoUnit`(이미 있음), `org.junit.jupiter.api.Assertions.assertEquals` 추가.

```kotlin
    private fun log(userId: UUID, sentAt: Instant, readAt: Instant? = null, title: String = "제목") =
        NotificationLog(id = null, userId = userId, type = NotificationType.WEEKLY_SUMMARY, referenceId = UUID.randomUUID(), expoTicketId = "t", deliveryStatus = DeliveryStatus.PENDING, deliveryError = null, sentAt = sentAt, title = title, body = "본문", readAt = readAt)

    // 30일 이내 알림만 sentAt 내림차순으로 조회하고 totalElements를 반환하는지 확인
    @Test
    fun `findPageByUserIdSentAfter는 기준 이후 알림만 최신순으로 반환한다`() {
        val userId = UUID.randomUUID()
        val now = Instant.now()
        adapter.save(log(userId, now.minus(40, ChronoUnit.DAYS), title = "오래됨"))
        adapter.save(log(userId, now.minus(2, ChronoUnit.DAYS), title = "이틀전"))
        adapter.save(log(userId, now.minus(1, ChronoUnit.DAYS), title = "어제"))
        adapter.save(log(UUID.randomUUID(), now.minus(1, ChronoUnit.DAYS), title = "다른유저"))

        val page = adapter.findPageByUserIdSentAfter(userId, now.minus(30, ChronoUnit.DAYS), page = 0, size = 10)

        assertEquals(listOf("어제", "이틀전"), page.items.map { it.title })
        assertEquals(2L, page.totalElements)
    }

    // 페이지 크기 단위로 잘려서 반환되는지 확인
    @Test
    fun `findPageByUserIdSentAfter는 size 단위로 페이지를 나눈다`() {
        val userId = UUID.randomUUID()
        val now = Instant.now()
        (1..3).forEach { adapter.save(log(userId, now.minus(it.toLong(), ChronoUnit.HOURS), title = "n$it")) }

        val second = adapter.findPageByUserIdSentAfter(userId, now.minus(30, ChronoUnit.DAYS), page = 1, size = 2)

        assertEquals(listOf("n3"), second.items.map { it.title })
        assertEquals(3L, second.totalElements)
    }

    // 읽은 알림과 31일 전 알림은 안 읽은 개수에서 제외되는지 확인
    @Test
    fun `countUnreadByUserIdSentAfter는 안 읽은 최근 알림만 센다`() {
        val userId = UUID.randomUUID()
        val now = Instant.now()
        adapter.save(log(userId, now.minus(1, ChronoUnit.DAYS)))
        adapter.save(log(userId, now.minus(2, ChronoUnit.DAYS), readAt = now))
        adapter.save(log(userId, now.minus(31, ChronoUnit.DAYS)))

        assertEquals(1L, adapter.countUnreadByUserIdSentAfter(userId, now.minus(30, ChronoUnit.DAYS)))
    }

    // 본인 알림만 읽음 처리되고, 이미 읽은 알림은 최초 read_at을 유지하는지 확인
    @Test
    fun `markRead는 소유권을 확인하고 최초 읽은 시각을 유지한다`() {
        val owner = UUID.randomUUID()
        val saved = adapter.save(log(owner, Instant.now()))
        val first = Instant.now().minus(1, ChronoUnit.HOURS)

        assertEquals(false, adapter.markRead(UUID.randomUUID(), saved.id!!, first))
        assertEquals(true, adapter.markRead(owner, saved.id!!, first))
        assertEquals(true, adapter.markRead(owner, saved.id!!, Instant.now()))

        val readAt = adapter.findPageByUserIdSentAfter(owner, Instant.now().minus(30, ChronoUnit.DAYS), 0, 10).items.single().readAt
        assertEquals(first.toEpochMilli(), readAt!!.toEpochMilli())
    }

    // 존재하지 않는 id는 false를 반환하는지 확인
    @Test
    fun `markRead는 없는 id에 false를 반환한다`() {
        assertEquals(false, adapter.markRead(UUID.randomUUID(), UUID.randomUUID(), Instant.now()))
    }

    // 전체 읽음은 본인 안 읽은 행만 갱신하고 다른 유저 행은 건드리지 않는지 확인
    @Test
    fun `markAllRead는 본인 안 읽은 알림만 읽음 처리한다`() {
        val me = UUID.randomUUID()
        val other = UUID.randomUUID()
        val now = Instant.now()
        adapter.save(log(me, now.minus(1, ChronoUnit.HOURS)))
        adapter.save(log(me, now.minus(2, ChronoUnit.HOURS)))
        adapter.save(log(other, now.minus(1, ChronoUnit.HOURS)))

        val updated = adapter.markAllRead(me, now)

        assertEquals(2, updated)
        assertEquals(0L, adapter.countUnreadByUserIdSentAfter(me, now.minus(30, ChronoUnit.DAYS)))
        assertEquals(1L, adapter.countUnreadByUserIdSentAfter(other, now.minus(30, ChronoUnit.DAYS)))
    }
```

`NotificationDispatcherTest`에 추가(import `org.junit.jupiter.api.Assertions.assertEquals`, `org.mockito.ArgumentCaptor`는 쓰지 말고 답변에서 캡처):

```kotlin
    // 로그에 title/body가 저장되고 푸시 data에 type/referenceId가 실리는지 확인
    @Test
    fun `발송 시 로그에 title body가 저장되고 푸시 data에 type과 referenceId가 실린다`() {
        val sent = mutableListOf<PushMessage>()
        `when`(notificationSender.send(org.mockito.ArgumentMatchers.anyList())).thenAnswer { invocation ->
            val messages = invocation.getArgument<List<PushMessage>>(0)
            sent += messages
            messages.map { PushSendResult(token = it.token, ticketId = "t-${it.token}", error = null) }
        }
        val userId = UUID.randomUUID()
        deviceTokenRepository.upsert(DeviceToken(id = null, userId = userId, expoPushToken = "ExponentPushToken[data-${System.nanoTime()}]", platform = DevicePlatform.IOS, createdAt = Instant.now(), updatedAt = Instant.now()))
        val referenceId = UUID.randomUUID()

        dispatcher.dispatch(userId, NotificationType.WEEKLY_SUMMARY, referenceId, "제목", "본문")

        assertEquals(mapOf("type" to "WEEKLY_SUMMARY", "referenceId" to referenceId.toString()), sent.single().data)
        val saved = notificationLogRepository.findPageByUserIdSentAfter(userId, Instant.now().minusSeconds(60), 0, 10).items.single()
        assertEquals("제목", saved.title)
        assertEquals("본문", saved.body)
    }

    // referenceId가 없는 알림(미실행 알림)도 data.referenceId가 빈 문자열로 실리는지 확인
    @Test
    fun `referenceId가 null이면 푸시 data의 referenceId는 빈 문자열이다`() {
        val sent = mutableListOf<PushMessage>()
        `when`(notificationSender.send(org.mockito.ArgumentMatchers.anyList())).thenAnswer { invocation ->
            val messages = invocation.getArgument<List<PushMessage>>(0)
            sent += messages
            messages.map { PushSendResult(token = it.token, ticketId = "t-${it.token}", error = null) }
        }
        val userId = UUID.randomUUID()
        deviceTokenRepository.upsert(DeviceToken(id = null, userId = userId, expoPushToken = "ExponentPushToken[null-${System.nanoTime()}]", platform = DevicePlatform.IOS, createdAt = Instant.now(), updatedAt = Instant.now()))

        dispatcher.dispatch(userId, NotificationType.INACTIVITY_ALERT, null, "제목", "본문")

        assertEquals(mapOf("type" to "INACTIVITY_ALERT", "referenceId" to ""), sent.single().data)
    }
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :bali-infra:test --tests "com.bali.infra.notification.NotificationLogRepositoryAdapterTest" :bali-batch:test --tests "com.bali.batch.notification.NotificationDispatcherTest" --continue`
Expected: 컴파일 에러(`title`, `findPageByUserIdSentAfter` 등 없음).

- [ ] **Step 3: 구현**

`V27__add_inbox_columns_to_notification_log.sql`:

```sql
-- 알림함(2026-10-01): notification_log를 서버가 보낸 푸시 목록으로 재사용한다. 기존 행은 title/body가 빈 문자열이다.
ALTER TABLE notification_log
    ADD COLUMN title VARCHAR(100) NOT NULL DEFAULT '',
    ADD COLUMN body VARCHAR(255) NOT NULL DEFAULT '',
    ADD COLUMN read_at TIMESTAMPTZ;

CREATE INDEX idx_notification_log_user_sent ON notification_log (user_id, sent_at DESC);
CREATE INDEX idx_notification_log_user_unread ON notification_log (user_id) WHERE read_at IS NULL;
```

`NotificationLog.kt`: 마지막 필드 뒤에 추가.

```kotlin
    val sentAt: Instant,
    val title: String = "",
    val body: String = "",
    val readAt: Instant? = null,
```

`NotificationLogRepository.kt`: 인터페이스에 추가(파일 하단에 `NotificationLogPage`도 정의).

```kotlin
    // 기준 시각(since) 이후 알림을 sentAt 내림차순으로 페이지 조회
    fun findPageByUserIdSentAfter(userId: UUID, since: Instant, page: Int, size: Int): NotificationLogPage

    // 기준 시각 이후의 안 읽은 알림 개수
    fun countUnreadByUserIdSentAfter(userId: UUID, since: Instant): Long

    // 본인 소유 알림을 읽음 처리. 소유가 아니거나 없으면 false, 이미 읽었으면 기존 read_at을 유지하고 true
    fun markRead(userId: UUID, id: UUID, at: Instant): Boolean

    // 본인 안 읽은 알림 전체를 읽음 처리하고 갱신 건수를 반환
    fun markAllRead(userId: UUID, at: Instant): Int
}

// 알림함 페이지 조회 결과
data class NotificationLogPage(val items: List<NotificationLog>, val totalElements: Long)
```
(기존 마지막 `}`는 위 코드의 `}`로 대체한다.)

`NotificationLogJpaEntity.kt`: 생성자 마지막에 추가.

```kotlin
    var sentAt: Instant = Instant.now(),
    var title: String = "",
    var body: String = "",
    var readAt: Instant? = null,
```

`NotificationLogJpaRepository.kt`: import에 `org.springframework.data.domain.Page`, `Pageable`, `org.springframework.data.jpa.repository.Modifying`, `Query`, `org.springframework.data.repository.query.Param` 추가 후:

```kotlin
    fun findByUserIdAndSentAtAfter(userId: UUID, sentAt: Instant, pageable: Pageable): Page<NotificationLogJpaEntity>
    fun countByUserIdAndSentAtAfterAndReadAtIsNull(userId: UUID, sentAt: Instant): Long
    fun findByIdAndUserId(id: UUID, userId: UUID): NotificationLogJpaEntity?

    @Modifying(clearAutomatically = true)
    @Query("UPDATE NotificationLogJpaEntity n SET n.readAt = :at WHERE n.userId = :userId AND n.readAt IS NULL")
    fun markAllRead(@Param("userId") userId: UUID, @Param("at") at: Instant): Int
```

`NotificationLogRepositoryAdapter.kt`: import `NotificationLogPage`, `org.springframework.data.domain.PageRequest`, `org.springframework.data.domain.Sort`. `save`의 엔티티 생성에 `title = log.title, body = log.body, readAt = log.readAt` 추가, `toDomain`에 `title = title, body = body, readAt = readAt` 추가, 아래 메서드 추가.

```kotlin
    override fun findPageByUserIdSentAfter(userId: UUID, since: Instant, page: Int, size: Int): NotificationLogPage {
        val result = jpaRepository.findByUserIdAndSentAtAfter(userId, since, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "sentAt")))
        return NotificationLogPage(items = result.content.map { it.toDomain() }, totalElements = result.totalElements)
    }

    override fun countUnreadByUserIdSentAfter(userId: UUID, since: Instant): Long =
        jpaRepository.countByUserIdAndSentAtAfterAndReadAtIsNull(userId, since)

    @Transactional
    override fun markRead(userId: UUID, id: UUID, at: Instant): Boolean {
        val entity = jpaRepository.findByIdAndUserId(id, userId) ?: return false
        if (entity.readAt == null) entity.readAt = at
        return true
    }

    @Transactional
    override fun markAllRead(userId: UUID, at: Instant): Int = jpaRepository.markAllRead(userId, at)
```

`NotificationDispatcher.kt` `dispatch`: `PushMessage(...)` 생성에 `data = pushData`를 넣고 로그에 title/body를 저장한다.

```kotlin
        val pushData = mapOf("type" to type.name, "referenceId" to (referenceId?.toString() ?: ""))
        val results = notificationSender.send(tokens.map { PushMessage(token = it.expoPushToken, title = title, body = body, data = pushData) })
```
그리고 `NotificationLog(...)`에 `title = title, body = body,` 추가.

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :bali-infra:test :bali-batch:test`
Expected: 전부 PASS (기존 알림/러너 테스트 포함).

- [ ] **Step 5: Commit**

```bash
git add bali-core bali-infra bali-batch
git commit -m "feat(infra,batch): notification_log에 title/body/read_at 추가 및 푸시 data 전달"
```

---

### Task 2: 알림함 API (`NotificationInboxController`)

**Files:**
- Create: `bali-api/src/main/kotlin/com/bali/api/notification/NotificationInboxController.kt`, `NotificationInboxResponse.kt`
- Test: `bali-api/src/test/kotlin/com/bali/api/notification/NotificationInboxControllerTest.kt`

**Interfaces:**
- Consumes: Task 1의 `NotificationLogRepository` 메서드, `NotificationLogPage`, `NotificationLog.readAt`.
- Produces: `GET /api/v1/notifications?page&size`, `GET /api/v1/notifications/unread-count`, `PATCH /api/v1/notifications/{id}/read`, `POST /api/v1/notifications/read-all`.

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
package com.bali.api.notification

import com.bali.api.auth.jwt.JwtTokenProvider
import com.bali.core.notification.DeliveryStatus
import com.bali.core.notification.NotificationLog
import com.bali.core.notification.NotificationLogRepository
import com.bali.core.notification.NotificationType
import com.bali.core.user.AuthProvider
import com.bali.infra.user.UserJpaEntity
import com.bali.infra.user.UserJpaRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationInboxControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired lateinit var userJpaRepository: UserJpaRepository
    @Autowired lateinit var logRepository: NotificationLogRepository

    // 테스트용 사용자를 만들고 (JWT, userId)를 반환
    private fun newUser(): Pair<String, UUID> {
        val entity = userJpaRepository.save(
            UserJpaEntity(email = "inbox-test-${System.nanoTime()}@example.com", provider = AuthProvider.GOOGLE, providerId = "sub-inbox-${System.nanoTime()}")
        )
        return jwtTokenProvider.generateToken(entity.id, entity.email) to entity.id
    }

    // 알림 로그 1건을 저장하고 id 반환
    private fun saveLog(userId: UUID, sentAt: Instant, title: String = "제목", readAt: Instant? = null, referenceId: UUID? = UUID.randomUUID()): UUID =
        logRepository.save(
            NotificationLog(id = null, userId = userId, type = NotificationType.WEEKLY_SUMMARY, referenceId = referenceId, expoTicketId = "t", deliveryStatus = DeliveryStatus.PENDING, deliveryError = null, sentAt = sentAt, title = title, body = "본문", readAt = readAt)
        ).id!!

    @Test
    fun `토큰 없이 알림함 목록을 호출하면 401`() {
        mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `목록은 최근 30일 알림을 최신순으로 반환하고 응답 형태를 지킨다`() {
        val (token, userId) = newUser()
        val now = Instant.now()
        saveLog(userId, now.minus(31, ChronoUnit.DAYS), title = "오래됨")
        saveLog(userId, now.minus(2, ChronoUnit.DAYS), title = "이틀전")
        saveLog(userId, now.minus(1, ChronoUnit.DAYS), title = "어제", readAt = now)

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].title").value("어제"))
            .andExpect(jsonPath("$.items[0].read").value(true))
            .andExpect(jsonPath("$.items[0].type").value("WEEKLY_SUMMARY"))
            .andExpect(jsonPath("$.items[1].title").value("이틀전"))
            .andExpect(jsonPath("$.items[1].read").value(false))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.hasNext").value(false))
    }

    @Test
    fun `size와 page가 범위를 벗어나면 보정된다`() {
        val (token, userId) = newUser()
        (1..3).forEach { saveLog(userId, Instant.now().minus(it.toLong(), ChronoUnit.HOURS), title = "n$it") }

        mockMvc.perform(get("/api/v1/notifications").param("size", "2").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.hasNext").value(true))
        mockMvc.perform(get("/api/v1/notifications").param("size", "0").param("page", "-1").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.size").value(1))
            .andExpect(jsonPath("$.page").value(0))
        mockMvc.perform(get("/api/v1/notifications").param("size", "999").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.size").value(50))
    }

    @Test
    fun `referenceId가 없는 알림은 null로 내려간다`() {
        val (token, userId) = newUser()
        saveLog(userId, Instant.now(), referenceId = null)

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.items[0].referenceId").doesNotExist())
    }

    @Test
    fun `unread-count는 안 읽은 최근 알림 개수를 반환한다`() {
        val (token, userId) = newUser()
        val now = Instant.now()
        saveLog(userId, now.minus(1, ChronoUnit.DAYS))
        saveLog(userId, now.minus(2, ChronoUnit.DAYS), readAt = now)
        saveLog(userId, now.minus(31, ChronoUnit.DAYS))

        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.count").value(1))
    }

    @Test
    fun `PATCH read는 본인 알림을 읽음 처리하고 204를 반환한다`() {
        val (token, userId) = newUser()
        val id = saveLog(userId, Instant.now())

        mockMvc.perform(patch("/api/v1/notifications/$id/read").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)
        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.count").value(0))
    }

    @Test
    fun `다른 유저의 알림 id로 PATCH read하면 404이고 읽음 처리되지 않는다`() {
        val (token, _) = newUser()
        val (_, otherId) = newUser()
        val otherLog = saveLog(otherId, Instant.now())

        mockMvc.perform(patch("/api/v1/notifications/$otherLog/read").header("Authorization", "Bearer $token"))
            .andExpect(status().isNotFound)
        assert(logRepository.countUnreadByUserIdSentAfter(otherId, Instant.now().minus(30, ChronoUnit.DAYS)) == 1L)
    }

    @Test
    fun `read-all은 본인 안 읽은 알림을 전부 읽음 처리한다`() {
        val (token, userId) = newUser()
        saveLog(userId, Instant.now().minus(1, ChronoUnit.HOURS))
        saveLog(userId, Instant.now().minus(2, ChronoUnit.HOURS))

        mockMvc.perform(post("/api/v1/notifications/read-all").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)
        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.count").value(0))
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :bali-api:test --tests "com.bali.api.notification.NotificationInboxControllerTest"`
Expected: FAIL (404/405: 엔드포인트 없음).

- [ ] **Step 3: 구현**

`NotificationInboxResponse.kt`:

```kotlin
package com.bali.api.notification

import com.bali.core.notification.NotificationLog
import com.bali.core.notification.NotificationType
import java.time.Instant
import java.util.UUID

// 알림함 항목 1건을 HTTP 응답으로 변환하는 DTO
data class NotificationItemResponse(
    val id: UUID,
    val type: NotificationType,
    val title: String,
    val body: String,
    val referenceId: UUID?,
    val sentAt: Instant,
    val read: Boolean,
) {
    companion object {
        // NotificationLog 도메인 모델을 알림함 항목 응답으로 변환
        fun from(log: NotificationLog) = NotificationItemResponse(
            id = log.id!!, type = log.type, title = log.title, body = log.body,
            referenceId = log.referenceId, sentAt = log.sentAt, read = log.readAt != null,
        )
    }
}

// 알림함 목록 페이지 응답 DTO
data class NotificationPageResponse(
    val items: List<NotificationItemResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val hasNext: Boolean,
)

// 안 읽은 알림 개수 응답 DTO
data class UnreadCountResponse(val count: Long)
```

`NotificationInboxController.kt`:

```kotlin
package com.bali.api.notification

import com.bali.api.auth.currentUserId
import com.bali.core.notification.NotificationLogRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

// 서버가 보낸 푸시 알림함(목록/안 읽은 개수/읽음 처리) API 엔드포인트를 처리하는 REST 컨트롤러
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notification Inbox", description = "알림함 API")
class NotificationInboxController(
    private val logRepository: NotificationLogRepository,
) {
    companion object {
        private const val MAX_SIZE = 50
        private const val WINDOW_DAYS = 30L
    }

    // 알림함 노출 기준 시각(최근 30일)
    private fun windowStart(): Instant = Instant.now().minus(WINDOW_DAYS, ChronoUnit.DAYS)

    // 최근 30일 알림 목록 (sentAt 내림차순). page<0은 0, size는 1..50으로 보정
    @Operation(summary = "알림함 목록 조회", description = "최근 30일 알림을 최신순으로 조회한다. size 기본 20, 최대 50")
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): NotificationPageResponse {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, MAX_SIZE)
        val result = logRepository.findPageByUserIdSentAfter(currentUserId(), windowStart(), safePage, safeSize)
        return NotificationPageResponse(
            items = result.items.map { NotificationItemResponse.from(it) },
            page = safePage, size = safeSize, totalElements = result.totalElements,
            hasNext = (safePage + 1).toLong() * safeSize < result.totalElements,
        )
    }

    // 안 읽은 알림 개수 (최근 30일)
    @Operation(summary = "안 읽은 알림 개수", description = "최근 30일 중 안 읽은 알림 개수")
    @GetMapping("/unread-count")
    fun unreadCount(): UnreadCountResponse =
        UnreadCountResponse(logRepository.countUnreadByUserIdSentAfter(currentUserId(), windowStart()))

    // 알림 1건 읽음 처리. 본인 소유가 아니거나 없으면 404(존재 노출 방지)
    @Operation(summary = "알림 읽음 처리", description = "본인 소유가 아니거나 없으면 404, 이미 읽었으면 최초 읽은 시각을 유지한다")
    @PatchMapping("/{id}/read")
    fun markRead(@PathVariable id: UUID): ResponseEntity<Void> =
        if (logRepository.markRead(currentUserId(), id, Instant.now())) ResponseEntity.noContent().build()
        else ResponseEntity.notFound().build()

    // 본인 안 읽은 알림 전체 읽음 처리
    @Operation(summary = "알림 전체 읽음 처리", description = "본인의 안 읽은 알림을 모두 읽음 처리한다")
    @PostMapping("/read-all")
    fun readAll(): ResponseEntity<Void> {
        logRepository.markAllRead(currentUserId(), Instant.now())
        return ResponseEntity.noContent().build()
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :bali-api:test --tests "com.bali.api.notification.NotificationInboxControllerTest"`
Expected: PASS. 이어서 `./gradlew :bali-api:test`로 api 전체 확인(Swagger dev 테스트 1개만 환경 문제로 실패 가능).

- [ ] **Step 5: Commit**

```bash
git add bali-api
git commit -m "feat(api): 알림함 목록/안 읽은 개수/읽음 처리 API 추가"
```

---

### Task 3: 전체 검증, 프론트 전달, 정리

**Files:** 코드 변경 없음.

- [ ] **Step 1:** `./gradlew test --continue` 로 전 모듈 확인. 실패는 Swagger dev 프로필 테스트(환경 변수 부재) 1개만 허용하고, 그 외는 원인을 고친다.
- [ ] **Step 2:** 스펙 `docs/superpowers/specs/2026-10-01-notification-inbox-design.md`와 계획서를 커밋한다.
- [ ] **Step 3:** 로컬 API 서버를 재기동(`./gradlew :bali-api:bootRun`)해 V27을 적용하고 `GET /api/v1/notifications` 401 응답을 확인한다.
- [ ] **Step 4:** `SendMessage`로 `bali-frontend-8f`에 확정 계약(스펙의 "API" 섹션 전문과 `data` 페이로드, 로컬 서버 재기동 완료)을 전달한다.
- [ ] **Step 5:** 세션 마무리 시 앱 서버, Gradle 데몬, docker compose를 중지한다(사용자가 계속 로컬 테스트 중이면 유지).
