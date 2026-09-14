# 배치 AWS 배포 — EventBridge Scheduler + ECS RunTask 설계

## 배경

`bali-batch`는 로컬 개발 환경에서 Airflow(LocalExecutor)가 트리거하는 구조로 이미 붙어 있었다
(`docs/specs/2026-08-24-airflow-batch-orchestration-design.md`). 하지만 이건 로컬 한정 구성이고,
[[2026-08-27-dev-environment-cicd-design]]으로 `bali-api`가 AWS dev 환경(ECS Fargate)에 상시
배포된 뒤에도 배치는 여전히 개발자 로컬 머신에서만 돌고 있었다. 로컬 머신이 꺼져 있으면 주간/월간
분석, 비활성 알림, 루틴 리마인더가 전혀 실행되지 않는 상태였다.

`bali-frontend`가 리포트 탭(근육군별 집중도 등)에서 배치가 생성한 `WeeklyAnalysis`/`MonthlyAnalysis`
데이터를 실제로 렌더링하기 시작하면서, dev 환경에서도 배치가 정기적으로 돌아야 한다는 필요가
구체화됐다.

## 왜 Airflow를 AWS에 그대로 올리지 않았는가

가장 먼저 검토한 선택지는 로컬 Airflow 구성을 그대로 AWS에 옮기는 것(자체 호스팅 Airflow 또는
Amazon MWAA)이었다. 두 경우 모두 스케줄러/웹서버가 상시 가동돼야 하는 상시 비용 구조다 — MWAA는
가장 작은 환경(mw1.small)도 월 고정비가 발생하고, 자체 호스팅도 최소 ECS 태스크 1개 이상을
24시간 유지해야 한다.

`bali-batch`의 실제 작업량은 하루 최대 몇 분(잡 6개, 각각 수 초~2분 내외)뿐이라, 상시 가동
오케스트레이터를 유지하는 비용 대비 얻는 게 거의 없다고 판단했다. 개인 프로젝트 dev 환경이라는
규모를 고려해 **Airflow를 포기하고 완전 서버리스(사용한 시간만 과금) 구조로 전환**하기로 결정했다.
로컬 Airflow 구성(`docker-compose.yml`, `airflow/dags/`)은 그대로 남겨둔다 — 로컬에서 배치를
수동/반복 실행하며 개발할 때는 여전히 유용하고, 제거할 이유가 없다.

## 아키텍처

```
EventBridge Scheduler (잡별 cron, Asia/Seoul)
  → ECS RunTask (Fargate, on-demand — 상시 가동 태스크 없음)
    → bali-batch 컨테이너 (command override로 잡 선택)
      → RDS PostgreSQL (bali-api와 동일 인스턴스)

ECR: bali-batch 컨테이너 이미지 저장소
CloudWatch Logs: /ecs/bali-dev-bali-batch
```

`bali-batch`는 원래부터 "웹서버 없이 뜨고 종료 코드만 반환하는 순수 실행형" 애플리케이션으로
설계돼 있어(로컬 DockerOperator와 동일한 이미지 재사용), 별도 오케스트레이터 없이도 ECS
`containerOverrides.command`만으로 실행할 잡을 고를 수 있다. `BaliBatchApplication.kt`이 첫
번째 non-flag CLI 인자로 러너를 선택하는 기존 구조를 그대로 활용한다.

## 결정 사항

### 1. 스케줄러: EventBridge Scheduler (Airflow 대체)

`aws_scheduler_schedule` 리소스로 잡마다 독립된 cron 스케줄을 등록한다. 호출당 과금이 사실상
없고(무료 티어로 충분), 상시 가동 컴포넌트가 전혀 없다. 대신 Airflow가 제공하던 DAG 의존성
체이닝(태스크 A 성공 후 B 실행)은 없다 — 아래 "잡 의존성" 항목에서 시간 오프셋으로 근사한다.

### 2. 컴퓨트: ECS RunTask (Fargate, on-demand)

`bali-api`처럼 상시 서비스(`aws_ecs_service`)를 만드는 대신, EventBridge Scheduler가 직접
`ecs:RunTask`를 호출해 그때그때 새 태스크를 띄우고 끝나면 자동 종료된다. 태스크 정의
(`aws_ecs_task_definition.bali_batch`, cpu=256/memory=512)는 `bali-api`와 별개지만, **실행
역할(`execution_role_arn`)은 `aws_iam_role.ecs_task_execution`을 그대로 재사용**한다 — 이미
ECR pull + Secrets Manager read 권한을 갖고 있고, `bali-batch`가 필요한 권한이 정확히 그
부분집합이라 별도 롤을 만들면 동일 정책을 중복 정의하게 되기 때문이다.

네트워크 구성은 `bali-api`와 동일하게 퍼블릭 서브넷 + 퍼블릭 IP 자동 할당(`aws_security_group.ecs`
재사용) — NAT 게이트웨이 없이 아웃바운드(이미지 pull, RDS 접속, Secrets Manager 호출)를 처리한다.

### 3. IAM: EventBridge Scheduler 전용 롤

EventBridge Scheduler가 `ecs:RunTask`를 호출하려면 스케줄러 자신이 assume할 롤이 별도로
필요하다. `aws_iam_role.eventbridge_scheduler`(principal: `scheduler.amazonaws.com`)를 새로
만들고, `ecs:RunTask`(태스크 정의 ARN, 리비전 와일드카드)와 `iam:PassRole`(ECS 태스크 실행
롤에 대해)만 최소 권한으로 부여한다.

### 4. 잡 목록과 스케줄

| 잡 | cron (Asia/Seoul) | 비고 |
|---|---|---|
| `weekly` | 매주 월요일 00:00 | 주간 분석 집계 |
| `weekly-summary-push` | 매주 월요일 00:10 | `weekly` 완료를 기다리는 10분 오프셋 |
| `monthly` | 매월 1일 00:00 | 월간 분석 집계 |
| `monthly-summary-push` | 매월 1일 00:10 | `monthly` 완료를 기다리는 10분 오프셋 |
| `inactivity-alert` | 매일 00:00 | 비활성 유저 알림 |
| `routine-reminder` | 매일 06:00, 18:00 | 루틴 리마인더 |

**잡 의존성:** `weekly`→`weekly-summary-push`, `monthly`→`monthly-summary-push`처럼 순서가
있는 잡은 Airflow의 태스크 체이닝 대신 **몇 분 간격을 둔 별도 스케줄로 근사**한다. 개인
프로젝트 규모라 각 배치가 몇 분 안에 끝나서(실측: `weekly` 콜드스타트 포함 약 2분 40초 —
아래 검증 항목 참고) 레이스 컨디션 리스크가 낮다고 판단했다. 실행 시간이 늘어나 오프셋을
넘기는 경우가 생기면 재검토가 필요하다.

cron은 서버 타임존이 아니라 **앱 코드와 동일하게 `Asia/Seoul` 로컬시간**으로 직접 지정한다
(`schedule_expression_timezone`) — UTC 환산 실수를 피하기 위함이다.

### 5. CD 파이프라인: 이미지 빌드/푸시만, 재배포 트리거 없음

`.github/workflows/cd.yml`에 `deploy-batch` job을 추가했다. `bali-api`의 `deploy` job과 달리
**ECS 서비스 업데이트 단계가 없다** — 상시 서비스가 아니라 EventBridge가 그때그때 새 태스크를
띄우는 구조라 재배포 개념 자체가 없다. `develop` 브랜치에 푸시되면 `bali-batch/Dockerfile`로
이미지를 빌드해 `:${GIT_SHA}`와 `:latest` 두 태그로 ECR에 푸시하기만 하면, 다음 스케줄 실행
때 ECS가 `:latest`를 그대로 pull한다.

## 검증

`terraform apply` 이후 실제 cron 발동을 기다리지 않고, `aws ecs run-task`로 EventBridge
Scheduler가 하는 것과 동일한 호출(태스크 정의 ARN, 네트워크 설정, `containerOverrides`)을
수동으로 실행해 파이프라인 전체를 즉시 검증했다.

- `weekly` 잡을 수동 트리거 → ECS 태스크 `RUNNING` → `STOPPED`, exit code `0`
- CloudWatch Logs(`/ecs/bali-dev-bali-batch`)에서 RDS 연결, Flyway 검증(22개 마이그레이션),
  `WeeklyAnalysisRunner` 정상 실행/종료 확인
- 총 소요 시간 약 2분 40초 (Fargate 콜드스타트 + JVM 부팅 약 86초 포함) — 스케줄 자체엔
  지장 없지만 위 "잡 의존성" 오프셋 산정의 근거 수치

## 모니터링 확인 위치 (AWS 콘솔)

1. **EventBridge → Scheduler → Schedules** — 등록된 6개 스케줄, 다음 실행 예정 시각
2. **ECS → Clusters → bali-dev-cluster → Tasks (Stopped 포함)** — 개별 실행 이력(시작/종료
   시각, exit code)
3. **CloudWatch → Log groups → `/ecs/bali-dev-bali-batch`** — 실제 실행 로그
4. **ECR → Repositories → `bali-dev-bali-batch`** — CD가 푸시한 이미지 태그 목록

## 범위 밖

- 프로덕션 환경의 배치 스케줄링 (별도 스펙에서 재검토)
- 잡 실패 시 알림(Slack/이메일) 연동 — 현재는 CloudWatch Logs 수동 확인에 의존
- Airflow 기반의 진짜 태스크 의존성/재시도 정책 — 위 "잡 의존성" 오프셋 근사로 대체
- 로컬 Airflow 구성 제거 — 로컬 개발용으로 계속 유지
