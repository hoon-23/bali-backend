# Dev 환경 CI/CD 설계

## 배경

지금까지는 `bali-api`를 로컬(개발자 머신)에서만 구동해왔다. `bali-frontend`가 실기기/시뮬레이터에서
지속적으로 API를 호출하는 단계에 들어서면서, 로컬 개발자 머신이 꺼지면 프론트 작업이 막히는 문제가
생겼다. 이 문서는 프로덕션이 아닌, **상시 가동되는 dev 환경**을 구성하기 위한 설계다.

GitHub Actions 기반 CI(`.github/workflows/ci.yml`)는 이미 push/PR마다 전 모듈 테스트를 자동 실행하고
있다. 이 문서가 다루는 범위는 그 다음 단계인 **CD(배포)** 다.

## 범위

- dev 환경(상시 가동, 소규모 트래픽) 인프라 구성
- GitHub Actions로 develop 브랜치 배포 자동화
- 프로덕션(`main`/`popcorn_YYYYMMDD` 브랜치)은 별도 스코프 — 이 문서에서 다루지 않는다

## 아키텍처

```
CloudFront (기본 도메인, HTTPS)
  → ALB
    → ECS Fargate (bali-api 컨테이너, 태스크 1개 고정)
      → RDS PostgreSQL (db.t4g.micro, Single-AZ)

Secrets Manager: JWT_SECRET / GOOGLE_CLIENT_ID / APPLE_BUNDLE_ID / KAKAO_CLIENT_ID / RDS 자격증명
ECR: bali-api 컨테이너 이미지 저장소
```

## 결정 사항

### 1. 컴퓨트: ECS Fargate

AWS App Runner는 2026년 3월 유지보수 모드로 전환되며 신규 고객 등록이 막혀 선택지에서 제외했다.
남은 컨테이너 배포 경로 중 ECS Fargate(Express Mode)를 선택했다 — 태스크 정의 등 ECS 고유의 절차를
상당 부분 단순화해주면서도, 이후 오토스케일링·다중 태스크 등으로 확장할 때 별도 마이그레이션 없이
그대로 확장 가능하다.

현재는 사용자 수가 적을 것으로 예상되는 초기 단계라 오토스케일링 없이 **태스크 1개 고정**으로 시작한다.

### 2. 데이터베이스: RDS PostgreSQL

같은 인스턴스에 컨테이너로 Postgres를 직접 띄우는 대신 관리형 RDS를 선택했다 — 백업/스냅샷, 패치,
장애 복구가 자동화되어 애플리케이션과 데이터베이스의 생명주기가 분리된다. `db.t4g.micro`, Single-AZ로
시작한다(현재 트래픽 규모에서 Multi-AZ는 과설계).

### 3. 시크릿 관리: AWS Secrets Manager

`JWT_SECRET`, OAuth `client-id`(Google/Apple/Kakao), RDS 자격증명을 코드나 태스크 정의에 평문으로
넣지 않고 Secrets Manager에 저장한다. ECS 태스크 정의는 ARN으로 참조만 하고, 실제 값은 런타임에
주입된다. IAM으로 접근을 통제하고, 자동 로테이션도 필요 시 설정 가능하다.

### 4. 도메인/HTTPS: CloudFront 기본 도메인

dev 환경은 프론트엔드 개발/테스트 용도로만 호출되므로 커스텀 도메인을 구매하지 않는다. CloudFront를
ALB 앞단에 두면 생성 즉시 `*.cloudfront.net` 도메인에 AWS가 자동 발급한 인증서로 HTTPS가 적용된다.
모바일 클라이언트(iOS ATS 등)가 요구하는 HTTPS 조건을 별도 비용/설정 없이 충족한다.

커스텀 도메인은 실제 프로덕션 배포 시점에 별도로 검토한다 — 그때까지는 다음 이유로 급하지 않다:
- 앱스토어 심사는 백엔드 도메인을 특정하지 않는다(HTTPS로 정상 응답하면 충족)
- 이 프로젝트의 OAuth 로그인은 클라이언트가 네이티브 SDK로 토큰을 발급받고 백엔드는 ID 토큰만
  검증하는 구조라, 각 소셜 로그인 제공자 콘솔에 리다이렉트 URI를 등록할 필요가 없다
- 프로덕션에서 커스텀 도메인이 필요한 이유는 클라이언트에 하드코딩된 API 주소를 인프라 변경과
  분리하기 위함인데, dev 환경은 재배포가 잦아 이 분리 효과가 크지 않다

### 5. 비용 관리

현재 AWS 계정은 신규 계정 정책(2025-07-15 이후) 적용 대상으로, $100 크레딧을 최대 6개월
(만료 2027-02-27) 안에 소진하는 구조다. 이 기간 안에 유료 전환 여부를 재검토해야 한다.

## 다음 단계

1. Terraform 또는 AWS CDK로 인프라 정의 (VPC, ECS, RDS, ALB, CloudFront, Secrets Manager, ECR)
2. GitHub Actions에 CD 워크플로우 추가 — `develop` 브랜치 push 시 이미지 빌드 → ECR 푸시 → ECS
   서비스 업데이트
3. `application-dev.yml`의 자리표시자 환경변수를 Secrets Manager 참조로 연결
4. Flyway 마이그레이션이 dev DB에 안전하게 적용되도록 배포 파이프라인에 순서 반영
