# 홈트(맨몸 매트 운동) 지원 설계

2026-10-01. bali-backend 변경 + bali-frontend 위임 항목.

## 목표
스크린샷 같은 맨몸 매트 운동(홈트)을 기록하고, 기록이 통계에서 0으로 사라지지 않게 한다.
FUNCTIONAL 근육군(2026-09-24)도 이 용도다. 측정 단위(시간) 추가는 하지 않는다.

## 결정 사항
- 시간으로 버티는 운동(플랭크, 사이드플랭크, 할로우홀드)은 카탈로그에서 삭제한다. reps 필수 구조와 맞지 않아 등록 시 에러가 난다.
- 맨몸 기록은 기존 구조 그대로 쓴다: STRENGTH, `weight=0`, 반복수 기준.
- 반복수 권장 범위(10~15 등)는 저장하지 않는다(YAGNI).
- 스크린샷에는 운동 이름이 없어서(부위 라벨만 있음) 부위별 흔한 맨몸 운동으로 시딩한다.

## 백엔드

### 1. V26 마이그레이션
- 삭제: 플랭크, 사이드플랭크, 할로우홀드. `session_logs`/`template_items`의 FK가 `ON DELETE RESTRICT`라 참조가 있으면 실패하므로, `session_logs`/`template_items` 어느 쪽에서도 참조되지 않는 행만 지우는 `DELETE ... WHERE NOT EXISTS`로 작성해 CD가 멈추지 않게 한다. 참조 중이라 남은 행은 배포 후 수동으로 정리한다. dev 앱 화면에서 9/25~9/30 세션에는 이 3종이 없는 것을 확인했다(그 이전 기록과 템플릿은 미확인).
- 시딩(GLOBAL, STRENGTH, `BODYWEIGHT`) 12종:
  - FUNCTIONAL: 버피, 숄더탭
  - CHEST: 푸시업/무릎
  - ABS: 크런치, 바이시클크런치, 리버스크런치, 레그레이즈
  - SHOULDER: 파이크푸시업/무릎
  - TRICEPS: 벤치딥(의자), 푸시업/클로즈
  - BACK: 리버스스노우엔젤, 프론Y레이즈
- 기존 `풀업`, `딥스`, `스쿼트/원레그`, `행잉레그레이즈`는 V22에서 이미 BODYWEIGHT 태그라 보정 불필요.

### 2. `GET /api/v1/exercises?equipment=`
`muscleGroup`과 같은 방식의 선택적 필터(`Equipment` enum). 둘을 같이 주면 AND.

### 3. 통계 보정
주간/월간은 `AnalysisSummary`를 공유한다. 무게 볼륨과 맨몸 지표는 단위가 달라 합산하지 않고 분리한다.
- `AnalysisSummary`에 필드 추가(기본값 있음, 기존 JSONB 행 호환):
  - `bodyweightRepsByExercise: Map<UUID, Int>` — 완료된 BODYWEIGHT STRENGTH 로그의 세트×반복수
  - `setsByMuscleGroup: Map<MuscleGroup, Int>` — 완료된 STRENGTH 로그의 근육군별 세트 수
  - `bodyweightRepsChangeFromLastWeekPercent: BigDecimal?` (이전 값이 0이거나 없으면 null)
- `volumeByExercise`/`volumeByMuscleGroup`/`volumeChangeFromLastWeekPercent`는 의미와 형태를 유지한다.
- 근육군 비중 인사이트는 `setsByMuscleGroup` 기준으로 바꾼다(일간 히트맵의 `completedSets`와 같은 기준).
- 맨몸 반복수 증감 인사이트를 추가한다(±10% 임계값, 기존 볼륨 인사이트와 동일).
- `MonthlyStatsCalculator`에도 같은 계산을 적용한다. `lifetime`은 무게를 쓰지 않아 변경 없음.

### 테스트
- 계산기 단위 테스트: 맨몸 로그만 있는 주/혼합 주/맨몸 없는 주, 근육군 비중이 세트 기준으로 계산되는지, 이전 주 값이 0일 때 null.
- JSONB 하위 호환: 신규 필드가 없는 기존 summary JSON이 역직렬화되는지.
- 종목 필터 컨트롤러 테스트, V26 마이그레이션이 빈 DB와 시드 DB 모두에서 통과하는지.

## 프론트(bali-frontend 세션으로 전달)
1. 종목 선택 화면에 홈트/맨몸 필터 칩 (`equipment=BODYWEIGHT`).
2. `equipment=BODYWEIGHT` 종목은 무게 입력란을 숨기고 `weight=0` 자동 전송.
3. FUNCTIONAL("기능성 근력운동") 근육군의 라벨, 색, 아이콘 추가.
4. 분석 화면에서 `bodyweightRepsByExercise`와 `setsByMuscleGroup`을 표시하고, 맨몸 종목은 볼륨 대신 총 반복수로 보여준다.
5. 삭제되는 3종(플랭크, 사이드플랭크, 할로우홀드)은 클라이언트 캐시나 하드코딩이 있으면 제거한다.
