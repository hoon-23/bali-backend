# 유저 레벨/경험치(EXP) 설계

2026-10-02. bali-backend 변경. 프론트 프로필 화면의 "Lv.N, 현재EXP/목표EXP" 목업을 실제 데이터로 채우는 게임화 요소다.
요청 출처: 사용자(규칙 결정)와 bali-frontend 세션(화면 요구사항, EXP 인정 조건 강화 요청을 사용자 결정이라고 전달, 확인 후 반영).

## 목표
- 운동 세션 완료에 경험치를 주고 레벨을 계산해 `GET /users/me`로 내려준다.
- 꾸준히 운동할수록 레벨업이 이어지도록 연속 운동 보너스를 둔다.
- 세션을 일부러 많이 등록하거나 기록 없이 종료만 눌러 EXP를 올리는 것을 막는다.

## 결정 사항
- **EXP는 저장하지 않고 조회 시 계산한다.** 레벨 테이블, enum, EXP 컬럼을 두지 않고 스키마도 바꾸지 않는다.
  인정 세션의 날짜별 이력만으로 EXP가 정해지므로(세션 수 + 연속일) 별도 저장이 필요 없고, 규칙을 바꿔도 마이그레이션이 필요 없으며, 세션 삭제나 상태 변경이 자동 반영된다.
- 레벨 곡선은 공식(`1000 × 1.05^(레벨-1)`)이라 테이블이나 enum으로 만들지 않는다. 레벨은 상한이 없어 enum이 맞지 않는다.
- 얻은 EXP/레벨업 연출은 프론트가 처리한다. 세션 완료 응답이 완료 전후 레벨 스냅샷을 내려준다.
- 평균(같은 날 여러 세션의 평균 EXP)안은 3세션을 해도 1회분이라 부당해서 기각하고, 하루 인정 세션 한도로 처리한다.
- 곡선 증가율은 ×1.10이 후반에 너무 느려(레벨 20까지 약 3.7년) ×1.05로 정했다.

## 규칙
| 항목 | 값 |
|---|---|
| 세션 기본 EXP | 100 |
| 인정 세션 | COMPLETED이고 아래 "인정 로그" 조건을 만족하는 로그가 1개 이상 |
| 하루 인정 세션 | 최대 2회 (3번째 세션부터 0 EXP) |
| 연속 운동일 | 운동일 사이 간격이 3일 이내면 연속 (연속 이틀 휴식까지 허용, 월수금/주 5회 분할이 주말에 끊기지 않음) |
| 연속 배수 | 연속 3번째 운동일부터 ×1.2 (세션당 120 EXP, 같은 날 2세션 모두 적용) |
| 연속 끊김 | 간격이 4일 이상 벌어지면 다음 운동일부터 1로 다시 시작 |
| 레벨 곡선 | 1→2레벨 1000 EXP, 이후 레벨마다 5% 증가(반올림) |
| 시작 | 기록이 없으면 Lv.1, 0 EXP |

날짜는 세션의 `date`(한국 기준 날짜)이고, 연속일은 인정 세션이 있는 날만 센다.

### 인정 로그 (기준일 2026-10-02)
- **기준일 이전 세션**: 완료 체크된(`completed=true`) 로그가 1개 이상이면 인정한다. 소급 적용하지 않는다.
- **기준일(2026-10-02) 이후 세션**: 완료 로그가 실제 수행 기록을 가져야 인정한다.
  - 근력(맨몸 포함): `actualSets > 0`이고 `actualReps > 0`
  - 유산소: `actualDurationSeconds > 0`
- 인정 로그가 없으면 0 EXP이고 `zeroReason`은 `NO_COMPLETED_LOG`다(의미: 인정할 완료 로그가 없음). 하루 한도와 연속일 계산에도 같은 조건을 적용한다.
- 소급하지 않는 이유: 기존 기록(과거 운동 일지를 옮긴 데이터)은 완료 체크만 있고 세트·횟수가 비어 있는 경우가 많다. 로컬 DB 실제 계정 기준으로 새 조건을 소급하면 인정 세션이 199개에서 58개로 줄어 레벨이 크게 내려간다.
- 기준일은 `UserLevel.STRICT_QUALIFICATION_FROM` 상수 한 곳이다.

### 부분 수행 (시작일 2026-10-03)
완료 인정 조건을 못 채운 COMPLETED 세션이라도 **기록된 수행량**이 기준 이상이면 50 EXP를 준다. 운동을 중간에 종료해도 한 일을 인정하기 위한 규칙이다.

| 항목 | 값 |
|---|---|
| 대상 | COMPLETED이고 정상 인정 조건(위 "인정 로그")을 만족하지 못한 세션. 세션 `date`가 2026-10-03 이후일 때만 (이전은 소급 없음) |
| 근력 기준 | `actualReps > 0`인 로그의 `actualSets` 합 ≥ 2. 완료 체크 여부와 무관하고 여러 종목의 세트를 합산한다 |
| 유산소 기준 | 로그의 `actualDurationSeconds` 합 ≥ 1200초(20분). 완료 체크 여부와 무관하다 |
| 인정 | 근력/유산소 중 하나만 충족해도 인정 |
| EXP | 세션당 50 고정. 연속 보너스(×1.2) 없음 |
| 하루 한도 | 정상 인정과 같은 2슬롯을 쓴다. 3개 이상이면 EXP가 큰 것부터 2개를 인정 |
| 연속 운동일 | 부분 수행만 있는 날은 연속에 포함하지 않고 정상 운동일 사이를 잇지도 못한다 |
| 통계 | 운동일, 완료 세션 수 등 통계는 바꾸지 않는다. EXP에만 적용 |

- 상수는 `UserLevel`에 한 곳씩 둔다: `PARTIAL_SESSION_EXP`, `PARTIAL_QUALIFICATION_FROM`, `PARTIAL_MIN_SETS`, `PARTIAL_MIN_CARDIO_SECONDS`(20분).
- 정상 완료(완료 체크 + 수행 기록)의 판정은 그대로다. 부분 수행은 정상 인정 로그가 하나도 없는 세션에만 적용한다.
- 클라이언트는 운동 종료 전에 완료 체크를 안 한 종목의 기록을 `completed` 없이 `PATCH /sessions/{id}/logs/{logId}`로 먼저 저장하고(서버는 null인 필드를 바꾸지 않으므로 `completed`는 그대로 유지된다), 그 뒤 `status=COMPLETED`를 보낸다. 서버 판정은 이 저장값을 쓴다.

## 레벨 표
레벨 n에 도달하는 데 필요한 누적 EXP. 주 3회 환산은 연속 보너스(120 EXP)가 계속 유지된다고 가정한 대략값이다.

| 레벨 | 다음 레벨까지 필요 EXP | 도달 누적 EXP | 100 EXP 세션 수 | 주 3회 환산 |
|---:|---:|---:|---:|---:|
| 1 | 1,000 | 0 | 0 | 0주 |
| 2 | 1,050 | 1,000 | 10 | 3주 |
| 3 | 1,103 | 2,050 | 21 | 6주 |
| 4 | 1,158 | 3,153 | 32 | 9주 |
| 5 | 1,216 | 4,311 | 44 | 12주 |
| 6 | 1,276 | 5,527 | 56 | 15주 |
| 7 | 1,340 | 6,803 | 69 | 19주 |
| 8 | 1,407 | 8,143 | 82 | 23주 |
| 9 | 1,477 | 9,550 | 96 | 27주 |
| 10 | 1,551 | 11,027 | 111 | 31주 |
| 15 | 1,980 | 19,599 | 196 | 54주 |
| 20 | 2,527 | 30,540 | 306 | 85주 |
| 25 | 3,225 | 44,503 | 446 | 124주 |
| 30 | 4,116 | 62,323 | 624 | 173주 |

첫 레벨업은 10회 완료(주 3회 기준 약 3주)로, 초반 레벨업이 빠르도록 시작 필요량을 1000으로 두고 증가율을 낮게(5%) 잡았다.

## API
### `GET /api/v1/users/me`, `PATCH /api/v1/users/me`
응답 최상위에 `level` 객체를 추가한다(전부 non-null Int, 기존 필드 변경 없음).
```json
"level": { "level": 5, "currentEXP": 1089, "EXPForNextLevel": 1216, "totalEXP": 5400 }
```
- `level`: 현재 레벨(1부터)
- `currentEXP`: 현재 레벨 안에서 쌓인 EXP (진행 바 분자)
- `EXPForNextLevel`: 현재 레벨에서 다음 레벨까지 필요한 전체 EXP (진행 바 분모, 레벨마다 다름)
- `totalEXP`: 누적 총 EXP

### `PATCH /api/v1/sessions/{id}`
이 요청으로 status가 COMPLETED로 **바뀔 때만** 응답에 `EXP` 객체를 넣는다. 그 외(이미 COMPLETED, status 없는 PATCH, 생성/조회/목록 응답)는 `null`이다.
```json
"EXP": {
  "earnedEXP": 120, "baseEXP": 100, "bonusEXP": 20,
  "zeroReason": null, "partial": false,
  "before": { "level": 5, "currentEXP": 650, "EXPForNextLevel": 1216, "totalEXP": 4960 },
  "after":  { "level": 5, "currentEXP": 770, "EXPForNextLevel": 1216, "totalEXP": 5080 }
}
```
- `earnedEXP = after.totalEXP - before.totalEXP = baseEXP + bonusEXP`. `baseEXP`는 정상 인정이면 100, 부분 수행이면 50, EXP를 못 얻었으면 0이다. 한도 때문에 더 작은 세션을 밀어내 실제 얻은 EXP가 기본값보다 작으면 `baseEXP`는 얻은 EXP와 같다(`bonusEXP` 0).
- `partial`은 이 세션이 부분 수행으로 인정되는 등급이면 `true`다(하루 한도로 0 EXP여도 `true`). 응답 필드는 추가만 했고 기존 필드와 값의 의미는 그대로다.
- 과거 날짜 세션을 완료해 뒤 날짜의 연속 보너스가 늘면 그 몫이 `bonusEXP`에 포함돼 20보다 클 수 있다.
- `zeroReason`은 `earnedEXP`가 0일 때만 값이 있다: `DAILY_LIMIT`(그날 이미 2세션 인정), `NO_COMPLETED_LOG`(인정할 완료 로그 없음, 부분 수행 기준도 못 채움).
- 진행 바는 before → after로 그리고, 레벨업은 `after.level > before.level`(여러 레벨 가능)이다.
- 로그 PATCH(`/sessions/{id}/logs/{logId}`) 응답에는 `EXP`가 없다.

## 구현
- core: `UserLevel`(EXP/레벨 계산, 상수), `EXPGain`/`EXPZeroReason`(완료 내역), `SessionLog.hasPerformanceRecord()`, `WorkoutSession.isEXPQualified()`, `WorkoutSessionRepository.countQualifiedSessionsByDate(userId, strictFrom)`.
- infra: 완료 로그가 있는 COMPLETED 세션의 날짜별 개수를 세는 집계 쿼리(기준일 이후는 수행 기록 조건 포함).
- api: `UserResponse.level`, `SessionResponse.EXP`.
- 부분 수행: `WorkoutSession.xpTier()`(FULL/PARTIAL/NONE)와 `hasPartialRecord()`, `WorkoutSessionRepository.countPartialSessionsByDate(...)`(infra는 인정 로그가 없고 수행량이 기준 이상인 COMPLETED 세션을 세션당 1행으로 집계). `UserLevel.fromSessionCounts(정상, 부분)`이 하루 2슬롯과 연속을 계산한다.
- 인정 조건은 쿼리(집계)와 `isEXPQualified()`(완료 응답의 `zeroReason` 판정) 두 곳에 있으므로 함께 바꿔야 한다.

## 테스트
- `UserLevelTest`: 하루 한도, 연속 3번째부터 ×1.2, 월수금 연속 유지, 4일 간격 끊김, 레벨 경계(999/1000 EXP), EXP 내역, 기준일 전후 인정 조건.
- 어댑터: 기준일 이전/이후, ABANDONED/미완료 제외, 유산소 시간 인정.
- 부분 수행: `UserLevelTest`(50 EXP, 슬롯 우선순위, 연속 비포함, 등급 판정, 세트 합산·reps 제외), 어댑터(집계 경계: 세트 2개/유산소 1200초, 정상 세션·ABANDONED·시작일 이전 제외), 컨트롤러(완료 체크 없이 세트 2개 → 50 EXP·`partial=true`, 1세트·시작일 이전 → 0 EXP).
- 컨트롤러: `/users/me` level이 인정 세션만 세는지, 세션 완료 `EXP`(100, 한도 초과 0 EXP `DAILY_LIMIT`, 수행 기록 없음 `NO_COMPLETED_LOG`, 재요청 null, 기준일 전후).

## 이후 후보 (착수 전)
- 완료 응답이 아닌 곳에서도 EXP 내역이 필요하거나 조회가 무거워지면 추가 전용 이력 테이블(`EXP_ledger`)로 전환한다. 기존 사용자는 지금 계산 함수로 한 번에 백필할 수 있다.
- 세션 이력으로 재현되지 않는 보상(퀘스트/업적)이 생길 때도 같다.
- 5/10레벨 마일스톤(칭호/뱃지)은 코드의 작은 맵으로 시작하고, 배포 없이 바꿔야 할 때 테이블로 옮긴다.
