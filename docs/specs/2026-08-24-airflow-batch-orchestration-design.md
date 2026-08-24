# Airflow 배치 오케스트레이션 — 설계 문서

## 배경

`bali-batch` 모듈(`WeeklyAnalysisRunner`)은 처음부터 "Airflow가 트리거하는 순수 실행형 배치"를
전제로 설계돼 있다 — 웹서버 없이 뜨고, 실행 결과(성공/실패)를 프로세스 종료 코드로 반환한다
(`docs/specs/2026-08-07-weekly-analysis-design.md` 참고). 하지만 실제로는 Airflow가 붙어있지
않고, docker-compose에도 Postgres만 있을 뿐 스케줄러가 없어 매주 월요일 실행이 자동으로
일어나지 않는 상태였다. 이 문서는 로컬 개발 환경에 Airflow를 실제로 붙여서 `bali-batch`를
매주 트리거하는 구성을 정의한다.

기존 스펙(2026-08-07)은 "BashOperator로 `java -jar bali-batch.jar` 직접 실행"을 언급했지만,
실제 구현 단계에서 아래 이유로 DockerOperator로 바꾼다: bali-api와 동일하게 컨테이너 단위로
배포/재사용 가능하고, Airflow 컨테이너 환경에 JDK/Gradle을 따로 설치할 필요가 없다.

## 범위

- 로컬 개발 환경(docker-compose) 한정. 운영 배포(MWAA, 별도 서버 Airflow 등)는 범위 밖 —
  필요해지면 별도 스펙.
- `WeeklyAnalysisRunner`의 도메인 로직은 변경하지 않는다. 오케스트레이션 레이어만 추가.
- 신규 배치 잡 추가 없음 (지금 트리거 대상은 `WeeklyAnalysisRunner` 하나뿐).

## 왜 LocalExecutor인가

Airflow 공식 docker-compose 퀵스타트는 CeleryExecutor 기준으로 8개 컨테이너
(scheduler / dag-processor / api-server / worker / triggerer / init / postgres / redis)를
띄운다. 이건 분산 워커가 여러 대 필요한 팀 규모를 전제한 구성이다.

Airflow 3.x 아키텍처에서 실제로 필수인 컴포넌트는 **scheduler / dag-processor / api-server /
메타데이터 DB** 뿐이다. worker는 LocalExecutor를 쓰면 scheduler 프로세스 안에 포함되어 별도
컨테이너가 필요 없고, triggerer는 deferrable operator를 쓸 때만 필요하다. 이번 DAG은
DockerOperator 하나만 쓰고 deferrable operator를 쓰지 않으므로 worker/triggerer/redis/flower를
전부 뺀다 — 주 1회 배치 하나 돌리는 로컬 개발 환경에 맞는 실무적 축소다.

## 구성 요소

### Airflow (3.3.1, LocalExecutor)

- `airflow-init` — 1회성: 메타데이터 DB 마이그레이션 + 관리자 계정 생성
- `airflow-dag-processor` — DAG 파일 파싱/직렬화
- `airflow-scheduler` — 스케줄링 + LocalExecutor로 태스크 직접 실행
- `airflow-api-server` — UI + REST API, `http://localhost:8181`

공식 `apache/airflow` 이미지에는 `DockerOperator`가 기본 포함돼 있지 않으므로,
`airflow-scheduler`/`airflow-dag-processor`/`airflow-api-server`에
`_PIP_ADDITIONAL_REQUIREMENTS=apache-airflow-providers-docker` (또는 별도 이미지 빌드)로
`apache-airflow-providers-docker`를 추가한다.

메타데이터 DB는 별도 컨테이너를 새로 띄우지 않고, 기존 `bali-postgres` 컨테이너 안에 `airflow`
데이터베이스를 앱 DB(`bali`)와 분리해서 둔다. `bali-postgres`는 이미 볼륨에 데이터가 있어
`/docker-entrypoint-initdb.d` 스크립트가 재실행되지 않으므로, `airflow` DB 생성은 구현 단계에서
1회성 수동 작업(`CREATE DATABASE airflow`)으로 처리한다.

### `bali-batch` 컨테이너화

- `bali-batch/Dockerfile` 신설 — 멀티스테이지(gradle build → JRE 이미지 위에서 실행 jar 구동)
- `bali-batch/src/main/resources/application.yml`의 datasource 설정을 env var로 오버라이드
  가능하게 변경: `jdbc:postgresql://localhost:5432/bali` → `${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/bali}`
  (Spring Boot relaxed binding 사용, 코드 변경 없음). 로컬에서 `./gradlew :bali-batch:bootRun`으로
  직접 실행할 때는 기존처럼 localhost로 동작하고, 컨테이너로 띄울 때만 env var로 덮어쓴다.
- `docker compose build bali-batch`로 `bali-batch:local` 이미지만 생성 — compose 서비스로
  등록하되 자동 기동 대상에서는 빼고(`profiles`) 이미지 빌드 용도로만 쓴다. 실제 실행은
  DockerOperator가 매번 새 컨테이너로 띄운다.

### DAG

`airflow/dags/weekly_analysis_dag.py`

- `DockerOperator(image="bali-batch:local", image_pull_policy="never", ...)`
- `network_mode="bali_default"` — docker-compose.yml의 `name: bali` 프로젝트가 만드는 기본
  네트워크. 이 네트워크에 붙어야 `bali-batch` 컨테이너가 `postgres` 서비스명으로 접속 가능.
- `environment={"SPRING_DATASOURCE_URL": "jdbc:postgresql://postgres:5432/bali"}`
- `schedule="0 0 * * 1"` (매주 월요일 00:00), `retries=1`, `retry_delay=timedelta(minutes=5)`
- `WeeklyAnalysisRunner`가 이미 성공 0 / 실패 1로 종료 코드를 반환하도록 설계돼 있으므로,
  DockerOperator가 이 종료 코드를 그대로 태스크 성공/실패로 반영한다 (`auto_remove="success"` —
  실패 시 컨테이너 로그를 남겨두고 성공 시에만 정리).

### 호스트 도커 접근

`airflow-scheduler` 컨테이너에 `/var/run/docker.sock:/var/run/docker.sock`을 마운트한다.
DockerOperator가 호스트 도커 데몬에 `bali-batch` 컨테이너를 sibling으로 띄우는, 로컬 개발에서
가장 흔히 쓰는 패턴이다.

## docker-compose.yml 변경

기존 파일 하나에 서비스를 추가한다 (별도 compose 파일로 분리하지 않음 — `postgres`/네트워크
공유를 단순하게 유지하기 위해). 추가되는 서비스:

- `airflow-init`, `airflow-dag-processor`, `airflow-scheduler`, `airflow-api-server`
- `bali-batch` (빌드 전용, `profiles: ["build-only"]`)

`airflow/dags`를 각 Airflow 컨테이너에 볼륨 마운트.

## 에러 처리

- 유저 단위 실패는 이미 `WeeklyAnalysisRunner` 내부에서 격리된다 (try/catch + `hadFailure`
  플래그) — 한 유저가 실패해도 나머지는 계속 처리되고, 하나라도 실패했으면 프로세스 종료 코드
  1을 반환한다.
- Airflow 레벨에서는 이 종료 코드 1을 태스크 실패로 받아 1회 재시도한다. 재시도도 실패하면
  DAG run이 실패 상태로 남고 Airflow UI에서 확인/수동 재실행할 수 있다. 알림(Slack/이메일)
  연동은 범위 밖.

## 테스트/검증 계획

- `docker compose up`으로 Airflow 스택 기동 후 UI(`localhost:8181`) 접속 확인
- DAG 수동 트리거 1회 실행 → `bali-batch` 컨테이너 로그에서 `WeeklyAnalysisRunner` 실행 확인,
  `weekly_analyses`/`insights` 테이블에 결과 반영 확인
- 의도적 실패 케이스(예: 트리거 직전 `bali-postgres` 중지) 구성 → 재시도 1회 후 태스크가
  실패로 마킹되는지 확인

## 범위 밖

- 운영(prod) 배포용 Airflow 인프라 (MWAA, K8s 등)
- 알림(Slack/이메일) 연동
- `WeeklyAnalysisRunner` 외 다른 배치 잡 추가
