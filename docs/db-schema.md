# DB 스키마 (ERD)

Flyway 마이그레이션(V1~V22) 기준 전체 13개 테이블. GitHub에서 mermaid 블록이 자동 렌더링된다.

- **실선** — DB에 실제 `FOREIGN KEY` 제약이 있는 관계
- **점선** — 코드에서만 참조하고 DB 제약은 없는 관계 (대부분의 `user_id`/`exercise_id` 참조가 여기 해당 — 2026-09-24, `exercises`에서 PERSONAL 종목을 직접 삭제했을 때 `session_logs`/`template_items`에 고아 `exercise_id` 참조가 남은 게 이 구조 때문)

```mermaid
erDiagram
    USERS {
        uuid id PK
        varchar email UK
        varchar provider
        varchar provider_id
        varchar status
        varchar nickname
        int weekly_goal_sessions
        timestamptz created_at
    }
    EXERCISES {
        uuid id PK
        varchar name
        varchar variant
        varchar muscle_group
        varchar type
        varchar scope
        uuid owner_id FK
        varchar equipment
    }
    TEMPLATES {
        uuid id PK
        uuid user_id FK
        varchar category
        varchar name
        boolean deleted
    }
    TEMPLATE_ITEMS {
        uuid id PK
        uuid template_id FK
        uuid exercise_id FK
        int sort_order
        int target_sets
        int target_reps
        numeric target_weight
    }
    SESSIONS {
        uuid id PK
        uuid user_id FK
        date date
        uuid template_id FK
        varchar status
        int perceived_difficulty
    }
    SESSION_LOGS {
        uuid id PK
        uuid session_id FK
        uuid exercise_id FK
        int sort_order
        boolean completed
        jsonb set_timings
    }
    REFRESH_TOKENS {
        uuid id PK
        uuid user_id FK
        varchar token_hash UK
        timestamptz expires_at
        boolean revoked
    }
    WEEKLY_ANALYSES {
        uuid id PK
        uuid user_id FK
        date week_of
        varchar status
        jsonb summary
    }
    INSIGHTS {
        uuid id PK
        uuid analysis_id FK
        varchar summary_text
    }
    MONTHLY_ANALYSES {
        uuid id PK
        uuid user_id FK
        date month_of
        varchar status
        jsonb summary
    }
    MONTHLY_INSIGHTS {
        uuid id PK
        uuid analysis_id FK
        varchar summary_text
    }
    DEVICE_TOKENS {
        uuid id PK
        uuid user_id FK
        varchar expo_push_token UK
        varchar platform
    }
    NOTIFICATION_SETTINGS {
        uuid user_id PK
        boolean routine_reminder_enabled
        boolean inactivity_alert_enabled
        boolean summary_notification_enabled
    }
    NOTIFICATION_LOG {
        uuid id PK
        uuid user_id FK
        varchar type
        uuid reference_id
        varchar delivery_status
        timestamptz sent_at
    }

    TEMPLATES ||--o{ TEMPLATE_ITEMS : "template_id"
    TEMPLATES ||--o{ SESSIONS : "template_id"
    SESSIONS ||--o{ SESSION_LOGS : "session_id"
    WEEKLY_ANALYSES ||--o{ INSIGHTS : "analysis_id"
    MONTHLY_ANALYSES ||--o{ MONTHLY_INSIGHTS : "analysis_id"
    USERS ||--o{ REFRESH_TOKENS : "user_id"

    USERS ||..o{ EXERCISES : "owner_id"
    USERS ||..o{ TEMPLATES : "user_id"
    USERS ||..o{ SESSIONS : "user_id"
    USERS ||..o{ WEEKLY_ANALYSES : "user_id"
    USERS ||..o{ MONTHLY_ANALYSES : "user_id"
    USERS ||..o{ DEVICE_TOKENS : "user_id"
    USERS ||..|| NOTIFICATION_SETTINGS : "user_id"
    USERS ||..o{ NOTIFICATION_LOG : "user_id"
    EXERCISES ||..o{ TEMPLATE_ITEMS : "exercise_id"
    EXERCISES ||..o{ SESSION_LOGS : "exercise_id"
```

## 도메인별 구성

| 도메인 | 테이블 |
|---|---|
| 인증 | `users`, `refresh_tokens` |
| 운동 카탈로그 | `exercises` (GLOBAL/PERSONAL) |
| 루틴·세션 기록 | `templates`, `template_items`, `sessions`, `session_logs` |
| 분석 | `weekly_analyses`/`insights`, `monthly_analyses`/`monthly_insights` |
| 알림 | `device_tokens`, `notification_settings`, `notification_log` |
