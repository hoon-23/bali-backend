# 중단(ABANDONED) 세션 상태 설계

2026-10-01. bali-backend 변경. 요청 출처: bali-frontend 세션(사용자가 정한 정책이라고 전달, 2026-10-01).

## 문제
며칠 전 IN_PROGRESS 세션이 기록 탭에 계속 "진행중"으로 남는다(예: 9/24 세션이 일주일 넘게). 앱의 배너/홈 카드는 "오늘 날짜 세션만 진행중"으로 보는데 기록 탭만 상태 그대로 보여 불일치가 생긴다. 서버가 상태를 확정한다.

## 결정 사항
- 세션 상태에 `ABANDONED`(중단)를 추가한다. DB의 `sessions.status`는 VARCHAR(CHECK 제약 없음)라 마이그레이션이 필요 없다.
- 자정(KST)이 지난 IN_PROGRESS 세션을 **배치**가 ABANDONED로 전환한다(조회 시점 변환은 쓰지 않는다: 상태가 DB에 확정되고 KST 기준 계산이 한 곳에 모인다).
- 중단된 세션의 세트 기록(`actualSets/reps/weight`, `completed`)은 그대로 보존한다. 로그를 건드리지 않는다.
- 복구 경로는 그대로 둔다: `PATCH /sessions/{id}`의 status는 현재 전이 제한이 없어 `ABANDONED -> COMPLETED/SCHEDULED/IN_PROGRESS`가 이미 가능하다. 제한을 새로 만들지 않는다.
- 응답 변경 없음: 목록/상세의 `status`에 `"ABANDONED"` 값이 내려온다.

## 배치
- 신규 잡 `abandon-stale-sessions`: `status = IN_PROGRESS AND date < 오늘(KST)`인 세션을 ABANDONED로 일괄 갱신한다. 오늘 날짜 세션은 건드리지 않는다.
- 날짜 기준은 기존 `AnalysisPeriod.todayInApp()`(Asia/Seoul)을 쓴다(주간/월간 배치 UTC 버그와 같은 종류의 실수 방지).
- 실행 시각: 매일 00:05 KST. 멱등(재실행해도 이미 전환된 세션은 대상이 아님)이라 Airflow/EventBridge 재시도에 안전하다. 로그에 전환 건수를 남긴다.
- 인프라: `infra/terraform/batch.tf`의 스케줄 맵에 `abandon-stale-sessions` 추가(`cron(5 0 * * ? *)`, Asia/Seoul), 로컬용 Airflow DAG 추가. **Terraform apply는 별도 승인 후 실행한다**(CD는 이미지만 배포하므로 스케줄은 apply 전까지 생기지 않는다).

## 집계(분석)
- 완료 세션의 정의는 유지한다: `COMPLETED`만 완료 세션으로 센다(`completedSessionsCount`는 이미 `COMPLETED`만 센다).
- 주간/월간 분석에서 **완료율(completionRate)은 ABANDONED 세션의 로그를 분자/분모 모두에서 제외**한다(중단 세션이 완료율을 깎거나 부풀리지 않게).
- 볼륨, 운동 시간, 근육군 세트 수 같은 "실제로 한 일" 집계는 ABANDONED 세션이라도 `completed=true`인 로그를 그대로 포함한다(사용자가 실제로 수행한 기록이므로).
- `findActiveDates`/`findLastActiveDate`(미실행 알림, 히트맵)는 `completed=true` 로그 기준이라 변경하지 않는다.
- 구현: `WeeklyStatsCalculator`/`MonthlyStatsCalculator.calculate`에 `abandonedLogIds: Set<UUID> = emptySet()` 파라미터를 추가하고 완료율 계산에서만 제외한다. 러너와 컨트롤러(주간/월간, current 포함)가 ABANDONED 세션의 로그 id를 넘긴다.

## 테스트
- 어댑터: `date < today`인 IN_PROGRESS만 ABANDONED로 바뀌고(건수 반환), 오늘 날짜 IN_PROGRESS/SCHEDULED/COMPLETED/과거 COMPLETED는 그대로이며, 로그가 보존되는지.
- 러너: 어제 IN_PROGRESS는 ABANDONED, 오늘 IN_PROGRESS는 유지, 재실행 시 0건, KST 자정 경계(UTC 일요일 15:05 = KST 월요일 00:05).
- API: `PATCH`로 ABANDONED -> COMPLETED/SCHEDULED 전이가 되고 응답 status에 `ABANDONED`가 내려오는지.
- 계산기: ABANDONED 세션 로그를 `abandonedLogIds`로 넘기면 완료율에서 빠지고 볼륨에는 완료 로그가 포함되는지(주간/월간).

## 프론트(bali-frontend 세션)
기록 탭/상세의 "중단" 배지, 복구 버튼은 프론트가 이미 준비. 새 status 값 `ABANDONED` 외 응답 변경 없음. 배포 후 알림.
