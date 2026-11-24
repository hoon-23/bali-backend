# 설계서 목차

기능별 설계 문서입니다. **작성 시점의 기록**이라, 구조가 바뀐 뒤에는 현재 상태와 다를 수 있습니다.
현재 구조는 [architecture](../architecture.md)를 먼저 보세요.

- **유효**: 지금도 구현과 맞는 설계입니다.
- **일부 대체됨**: 일부 내용이 이후 설계로 바뀌었습니다. 비고의 문서를 함께 보세요.
- **구조 대체됨**: 당시 인프라 구조를 설명하며, 지금은 다른 구조입니다.

| 날짜 | 문서 | 상태 |
|------|------|------|
| 2026-08-03 | [운동 기록 분석 백엔드 설계 (Phase 1)](./2026-08-03-workout-insight-backend-design.md) | 유효 |
| 2026-08-05 | [운동 종목 카탈로그](./2026-08-05-exercise-catalog-design.md) | 유효 |
| 2026-08-06 | [운동 템플릿 / 세션 기록](./2026-08-06-workout-template-session-design.md) | 유효 |
| 2026-08-07 | [주간 배치 분석](./2026-08-07-weekly-analysis-design.md) | 일부 대체됨 |
| 2026-08-08 | [Swagger/OpenAPI 도입 + Spring Profile 분리](./2026-08-08-swagger-openapi-design.md) | 유효 |
| 2026-08-10 | [개인 종목 수정/삭제](./2026-08-10-exercise-update-delete-design.md) | 유효 |
| 2026-08-10 | [세션 삭제](./2026-08-10-session-delete-design.md) | 유효 |
| 2026-08-11 | [소셜 로그인 확장(네이버/카카오/애플)](./2026-08-11-social-login-design.md) | 유효 |
| 2026-08-17 | [사용자 프로필 확장](./2026-08-17-user-profile-expansion-design.md) | 유효 |
| 2026-08-18 | [세트 타이머](./2026-08-18-set-timer-design.md) | 유효 |
| 2026-08-24 | [Airflow 배치 오케스트레이션 (로컬 개발 환경)](./2026-08-24-airflow-batch-orchestration-design.md) | 유효 |
| 2026-08-24 | [소셜 로그인에서 백엔드가 client-id를 갖는 이유](./2026-08-24-oauth-client-id-audience-verification.md) | 유효 |
| 2026-08-25 | [푸시 알림 기능](./2026-08-25-push-notifications.md) | 유효 |
| 2026-08-27 | [Dev 환경 CI/CD](./2026-08-27-dev-environment-cicd-design.md) | 구조 대체됨 |
| 2026-09-14 | [배치 AWS 배포 (EventBridge Scheduler + ECS RunTask)](./2026-09-14-batch-aws-scheduling-design.md) | 구조 대체됨 |
| 2026-10-01 | [중단(ABANDONED) 세션 상태](./2026-10-01-abandoned-session-status-design.md) | 유효 |
| 2026-10-01 | [홈트(맨몸 매트 운동) 지원](./2026-10-01-home-training-bodyweight-design.md) | 유효 |
| 2026-10-01 | [알림함(푸시 목록 + 안 읽은 개수)](./2026-10-01-notification-inbox-design.md) | 유효 |
| 2026-10-02 | [주간/월간 분석 지표 재설계: 빈도와 강도 분리](./2026-10-02-analysis-frequency-intensity-design.md) | 유효 |
| 2026-10-02 | [기록 없는 세션을 운동 통계에서 제외](./2026-10-02-unrecorded-session-stats-exclusion-design.md) | 유효 |
| 2026-10-02 | [유저 레벨/경험치(EXP)](./2026-10-02-user-level-xp-design.md) | 유효 |

`일부 대체됨`: 주간 배치 분석의 지표(빈도·강도)는 [2026-10-02 분석 지표 재설계](./2026-10-02-analysis-frequency-intensity-design.md)로 바뀌었습니다.
