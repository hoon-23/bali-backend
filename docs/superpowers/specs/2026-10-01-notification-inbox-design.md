# 알림함(서버가 보낸 푸시 목록 + 안 읽은 개수) 설계

2026-10-01. bali-backend 변경. 요청 출처: bali-frontend 세션(사용자 확정이라고 전달, 2026-10-01).

## 목표
앱 홈 벨 아이콘에서 서버가 보낸 푸시 목록을 보고 안 읽은 개수 뱃지를 표시한다.
대상은 서버 푸시 전체(루틴 리마인더, 미실행 알림, 주간/월간 요약). 앱 로컬 알림(세트 타이머)은 제외.

## 결정 사항
- 새 테이블을 만들지 않고 `notification_log`에 컬럼을 추가한다. 이미 "발송 성공(티켓 접수) 시에만" 기록하므로 알림함이 곧 이 테이블이다.
- 알림함에는 Expo가 티켓을 접수한 발송만 보인다. 토큰이 없거나 해당 알림 종류가 꺼진 유저는 발송도 저장도 하지 않는다(현 동작 유지). Expo 영수증 확인이 없어서 기기에 실제 도착하지 않은 알림도 목록에는 보일 수 있다.
- 알림함은 최근 30일 알림만 보여준다(`sent_at` 기준). 행 자체는 중복 발송 방지(`existsByTypeAndReferenceId`, 쿨다운 조회)에 쓰이므로 지우지 않는다.
- 삭제/숨기기는 이번 범위에서 뺀다. 최근 30일 조회로 목록 길이를 제한한다. 필요해지면 `deleted_at` 컬럼과 소프트 삭제 API를 추가한다(프론트 로컬 숨김은 `unread-count`와 어긋나 채택하지 않는다).
- 읽음 시점은 프론트가 정해 API를 호출하고(예: 알림함 진입 시 `read-all`, 항목 탭 시 `PATCH`), 서버는 `read_at`을 저장하고 안 읽은 개수를 계산한다.

## 스키마 (V27)
`notification_log`에 추가:
- `title VARCHAR(100) NOT NULL DEFAULT ''`
- `body VARCHAR(255) NOT NULL DEFAULT ''`
- `read_at TIMESTAMPTZ NULL`
- 인덱스: `(user_id, sent_at DESC)` (목록), `(user_id) WHERE read_at IS NULL` (안 읽은 개수)

기존 행은 title/body가 빈 문자열이다. dev DB는 `notification_log`가 0건(2026-10-01 확인)이라 영향 없음.

## 백엔드 동작
- `NotificationLog`에 `title`, `body`(기본 `""`), `readAt`(기본 null) 추가.
- `NotificationDispatcher`가 로그 저장 시 title/body를 함께 저장하고, 푸시 `data`로 `{"type": "<NotificationType>", "referenceId": "<uuid|"">"}`를 같이 보낸다(`PushMessage.data`는 이미 지원되나 지금은 비어 있음). 앱이 푸시 탭과 알림함 항목을 같은 값으로 매칭할 수 있다.
- `NotificationLogRepository`에 추가: `findPageByUserId(userId, page, size)`, `countUnreadByUserId(userId)`, `markRead(userId, id, at): Boolean`, `markAllRead(userId, at): Int`. 목록/개수/읽음 처리는 `sent_at`이 최근 30일인 행만 대상으로 한다.

## API (`/api/v1/notifications`, 신규 `NotificationInboxController`)
- `GET /api/v1/notifications?page=0&size=20` — 최근 30일 알림을 `sentAt` 내림차순. `size` 기본 20, 최대 50(초과 시 50으로 보정).
  응답: `{ items: [{id, type, title, body, referenceId|null, sentAt, read}], page, size, totalElements, hasNext }`
- `GET /api/v1/notifications/unread-count` → `{ count }`
- `PATCH /api/v1/notifications/{id}/read` → 204. 본인 소유가 아니거나 없으면 404(존재 노출 방지). 이미 읽은 알림이면 기존 `read_at`을 유지(멱등).
- `POST /api/v1/notifications/read-all` → 204. 본인 안 읽은 알림만 갱신.
- 기존 `/device-token`, `/settings`와 경로가 겹치지 않는다.

## 테스트
- 어댑터: 페이지 정렬/hasNext, 안 읽은 개수, markRead 소유권/멱등, markAllRead가 다른 유저 행을 건드리지 않는지.
- 컨트롤러: 인증 필요, 목록 응답 형태, 다른 유저 알림 id로 PATCH 시 404, read-all 후 unread-count 0, 31일 전 알림은 목록과 개수에서 빠지는지.
- 디스패처: 저장된 로그에 title/body가 들어가고 푸시 `data`에 type/referenceId가 실리는지.

## 프론트(bali-frontend 세션)
홈 벨 아이콘 → 알림함 화면(목록, 읽음 처리), 뱃지는 `unread-count`. 푸시 탭 시 `data.type/referenceId`로 화면 이동. 확정 계약과 로컬 서버 재기동 시점은 구현 후 전달.
