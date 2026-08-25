# 푸시 알림 기능 설계

## 배경

bali-frontend가 프로필 설정/루틴 예약 기능을 붙이는 중인 상황에서, 서버가 능동적으로 사용자에게 알림을 보낼 채널이 필요해졌다. 이 문서는 v1 범위의 푸시 알림 기능(루틴 예약 리마인더, 운동 미실행/이탈 알림, 주간·월간 통계 요약)을 다룬다.

PR(개인 기록) 달성 알림은 이번 범위에서 제외한다. PR 감지 기능 자체가 아직 없고(`docs/specs`에 별도 스펙 없음), PR 기능을 논의할 때 함께 다시 검토한다.

월간 통계 집계(`MonthlyAnalysis`)는 이 설계 도중 별도 세션(`bali-backend-d8`)에 인계해 이미 구현 완료됐다. `WeeklyAnalysis`와 완전히 동일한 패턴이며, `GET /api/v1/analysis/monthly`, `GET /api/v1/analysis/monthly/{monthOf}`, `GET /api/v1/analysis/monthly/current`가 제공되고, `monthly_analysis` Airflow DAG가 매달 1일 00:00에 직전 달을 집계해 `SUCCESS`/`FAILED`/`NO_ACTIVITY` 상태로 저장한다. 이 문서는 그 결과물을 이미 존재하는 전제로 다룬다.

## 범위

**포함**
- 디바이스(Expo push token) 등록/해제
- 알림 유형별 on/off 설정 (3개)
- 루틴 예약 리마인더
- 운동 미실행/이탈 알림
- 주간·월간 통계 요약 알림

**제외 (v1 아님)**
- PR 달성 알림 (추후 PR 기능 논의 시)
- 인앱 알림함/알림 목록 UI (백엔드는 발송만 담당, 조회 API 없음)
- 영수증(receipt) 조회/기록 및 그에 기반한 재발송 — [오버스펙 우려](#오버스펙-우려-영수증-기록) 참조

## 네이밍

`notification`을 도메인명으로 쓴다. 패키지: `com.bali.core.notification` / `com.bali.infra.notification` / `com.bali.api.notification`. 테이블: `device_tokens`, `notification_settings`, `notification_log`. 발송 포트는 `NotificationSender`(도메인 인터페이스), 구현체는 실제 외부 서비스명을 따라 `ExpoPushSender`(bali-infra)로 한다 — 포트는 도메인 언어를, 어댑터는 실제 시스템 이름을 쓰는 기존 관례(`SocialTokenVerifier` 포트 vs `GoogleTokenVerifier`/`KakaoTokenVerifier` 구현체)를 따른 것이다.

새 Gradle 모듈은 만들지 않는다. 기존 4개 모듈(`bali-core`/`bali-infra`/`bali-api`/`bali-batch`)은 기술 레이어 분리이지 기능 도메인 분리가 아니며, `notification`도 다른 도메인(`session`, `analysis`, `template`)과 동일하게 이 4개 모듈 안에 패키지로만 존재한다.

## 아키텍처

기존 배치 컨벤션을 그대로 따른다: Airflow DAG가 `bali-batch:local` 컨테이너를 `DockerOperator`로 띄우고, 그 안에서 Spring Boot 배치 앱이 Runner를 실행해 repository로 DB에 직접 접근한다(`WeeklyAnalysisRunner`와 동일 패턴). bali-api를 거치는 HTTP 왕복은 없다.

```
Airflow DAG (cron)
  └─ DockerOperator → bali-batch:local
       └─ BaliBatchApplication.main(args) → args[0]로 Runner 선택
            └─ XxxRunner (bali-batch)
                 ├─ Repository 조회 (bali-core 포트, bali-infra 구현)
                 └─ NotificationSender.send() (bali-infra: ExpoPushSender)
```

`BaliBatchApplication.main()`은 현재 `WeeklyAnalysisRunner`를 하드코딩 실행한다. 이를 `args[0]`(runner key)로 실행할 Runner를 선택하도록 바꾼다. 기존 `weekly_analysis` DAG의 `DockerOperator`에도 `command=["weekly-analysis"]`를 명시적으로 추가해 하위 호환을 유지한다.

## 데이터 모델

### `device_tokens`
| 컬럼 | 타입 | 비고 |
|---|---|---|
| id | UUID PK | |
| user_id | UUID NOT NULL | FK 없음 (기존 테이블 관례상 소프트 참조) |
| expo_push_token | VARCHAR(255) UNIQUE NOT NULL | 토큰 기준 upsert. 재설치 시 동일 토큰이 다른 유저로 넘어갈 수 있어 유저 기준이 아닌 토큰 기준 유니크 |
| platform | VARCHAR(20) | IOS / ANDROID |
| created_at | TIMESTAMPTZ | |
| updated_at | TIMESTAMPTZ | |

인덱스: `user_id`.

### `notification_settings`
| 컬럼 | 타입 | 비고 |
|---|---|---|
| user_id | UUID PK | users와 1:1 |
| routine_reminder_enabled | BOOLEAN NOT NULL DEFAULT TRUE | |
| inactivity_alert_enabled | BOOLEAN NOT NULL DEFAULT TRUE | |
| summary_notification_enabled | BOOLEAN NOT NULL DEFAULT TRUE | 주간+월간 요약 공용 (별도 토글 아님) |

행이 없는 유저는 전부 기본값(TRUE)으로 취급한다(설정 화면을 아직 안 연 유저).

### `notification_log`
| 컬럼 | 타입 | 비고 |
|---|---|---|
| id | UUID PK | |
| user_id | UUID NOT NULL | |
| type | VARCHAR(30) NOT NULL | ROUTINE_REMINDER / INACTIVITY_ALERT / WEEKLY_SUMMARY / MONTHLY_SUMMARY |
| reference_id | UUID NULL | ROUTINE_REMINDER는 session id, WEEKLY/MONTHLY_SUMMARY는 analysis id, INACTIVITY_ALERT는 null |
| expo_ticket_id | VARCHAR(255) NULL | 발송 성공 시 기록 |
| delivery_status | VARCHAR(20) NOT NULL DEFAULT 'PENDING' | PENDING / DELIVERED / FAILED (영수증 조회 결과, 기록용) |
| delivery_error | VARCHAR(255) NULL | |
| sent_at | TIMESTAMPTZ NOT NULL | |

이 테이블은 두 가지 역할을 겸한다: **① 중복 발송 방지(idempotency)**, **② 발송 이력 기록**.

## 알림 유형별 로직

세 유형 모두 대상은 `UserStatus.ACTIVE`이고 해당 `notification_settings` 토글이 켜져 있는 유저다.

### 루틴 예약 리마인더
`sessions` 테이블에 시각 컬럼을 추가하지 않는다(프론트가 지금 붙이는 "루틴 예약"이 아직 시각까지는 안 받는 것으로 확인됨. 나중에 시각이 붙으면 재검토). 대신 하루를 오전(00~12시)/오후(12~24시) 두 구간으로 나누고, 각 구간의 절반 시점(06시, 18시 KST)에 그날 날짜로 `status=SCHEDULED`인 세션이 남아있으면 리마인더를 보낸다.

- DAG: `routine_reminder`, cron `0 21,9 * * *` (UTC 기준, KST 06/18시에 해당. `docker-compose.yml`에 타임존 설정이 없어 Airflow 컨테이너는 기본 UTC로 동작함을 확인함)
- Runner: `RoutineReminderRunner`
- 중복 방지: `notification_log`에 `type=ROUTINE_REMINDER AND reference_id=session.id`인 기록이 이미 있으면 스킵 (06시에 보냈으면 18시엔 안 보냄)

### 운동 미실행/이탈 알림
마지막으로 세션 로그가 완료된 시점이 7일 이상 지난 ACTIVE 유저에게 발송한다.

- DAG: `inactivity_alert`, 매일 1회 (예: 09시 KST)
- Runner: `InactivityAlertRunner`
- 중복 방지: `notification_log`에 `type=INACTIVITY_ALERT AND user_id=X`이고 `sent_at`이 최근 7일 이내인 기록이 있으면 스킵

### 주간/월간 통계 요약
별도 DAG를 새로 만들지 않고, 기존 `weekly_analysis`/`monthly_analysis` DAG에 태스크를 하나 추가한다(`run_xxx_analysis >> send_xxx_summary_push`). 해당 배치 실행에서 `status=SUCCESS`로 저장된 분석 대상 유저에게 발송한다.

- Runner: `WeeklySummaryPushRunner` / `MonthlySummaryPushRunner` (또는 공용 Runner + 타입 파라미터)
- 중복 방지: `notification_log`에 `type=WEEKLY_SUMMARY|MONTHLY_SUMMARY AND reference_id=analysis.id`인 기록이 있으면 스킵

## 발송 로직 및 에러 처리

`NotificationSender.send(tokens: List<String>, title, body, data): List<SendResult>` — Expo Push API에 최대 100개씩 배치 발송한다(`ExpoPushSender`, Java 11 `HttpClient` 사용, Expo는 별도 API 키 불필요).

- **티켓 단계 즉시 에러** (`DeviceNotRegistered`): 해당 토큰을 `device_tokens`에서 즉시 삭제. `notification_log`에는 기록하지 않는다(재시도 대상 아님 — 유저가 앱을 다시 열어 새 토큰을 등록하기 전까진 보낼 방법이 없음).
- **정상 접수**: `notification_log`에 `delivery_status=PENDING` + `expo_ticket_id`로 기록.
- **네트워크/5xx 등 일시적 실패**: `notification_log`에 기록하지 않고 예외를 던진다. `WeeklyAnalysisRunner`처럼 유저 단위로 try/catch해서 한 유저 실패가 다른 유저 발송을 막지 않게 하되, 하나라도 실패하면 Runner는 exit code 1을 반환한다. Airflow의 기존 재시도 설정(`retries: 1, retry_delay: 5분`)이 재시도를 수행하고, 이미 `notification_log`에 기록된 유저는 재시도 시 자동으로 스킵되므로 별도 재시도 큐가 없어도 중복 발송 없이 안전하게 재시도된다.

## 오버스펙 우려: 영수증 기록

Expo Push는 발송 즉시 받는 "티켓" 응답과, 별도로 조회해야 하는 "영수증(receipt)" 응답이 분리되어 있다. 실제 기기 전달 성공/실패는 영수증에서만 정확히 확인 가능하다(`DeviceNotRegistered`도 티켓 단계보다 영수증 단계에서 더 확실하게 잡힌다).

논의 중 "영수증까지 폴링해서 재발송까지 자동화"하는 안을 검토했으나, 새 상태머신·상시 폴링 DAG(30분 주기)가 필요해 리마인더/이탈 알림/통계 요약처럼 비트랜잭션·비필수 알림 대비 과한 인프라라고 판단해 **재발송 자동화는 v1에서 제외**했다.

**결정 (2026-08-25)**: 영수증 기록 배치(`NotificationReceiptRunner`)는 v1에서 제외한다. `notification_log.delivery_status` 컬럼은 스키마에 남겨두되(기본값 `PENDING`), 실제로 영수증을 조회해 갱신하는 로직은 이번 구현 범위에 포함하지 않는다. 운영 중 "왜 알림이 안 왔는지" 문의가 실제로 쌓이면 그때 별도로 추가한다.

## API (bali-api)

| Method | Path | 설명 |
|---|---|---|
| POST | `/api/v1/notifications/device-token` | Expo push token 등록/갱신 (토큰 기준 upsert) |
| DELETE | `/api/v1/notifications/device-token` | 로그아웃/앱 삭제 시 토큰 제거 |
| GET | `/api/v1/notifications/settings` | 3개 토글 조회 (행 없으면 기본값 TRUE로 응답) |
| PATCH | `/api/v1/notifications/settings` | 토글 변경 |

## 테스트

- `bali-batch`: `RoutineReminderRunnerTest`/`InactivityAlertRunnerTest`/`WeeklySummaryPushRunnerTest` — `WeeklyAnalysisRunnerTest` 패턴을 따라 `NotificationSender`를 fake로 대체해 검증
- `bali-api`: device-token 등록/삭제, settings 조회/변경 컨트롤러 테스트
- `bali-infra`: `DeviceTokenRepositoryAdapter`, `NotificationSettingsRepositoryAdapter`, `NotificationLogRepositoryAdapter` 테스트
