# 프라이빗 RDS로 1회성 데이터 마이그레이션하는 방법

2026-09-11, 로컬 DB의 개인 계정 2026년 운동 기록을 dev RDS로 이관하면서 정립한 방법. dev RDS는
프라이빗 서브넷에만 있고 `publicly_accessible = false`라 로컬 머신에서 직접 접속할 방법이 없다 —
보안그룹을 열어도 인터넷에서 라우팅 경로 자체가 없어서 소용없다(실제로 이 방식을 먼저 시도했다가
타임아웃만 겪었다).

## 핵심 아이디어

VPC 안에서 실행되는 **일회성 ECS Fargate 태스크**로 마이그레이션 스크립트를 돌린다. 기존 ECS
보안그룹(`aws_security_group.ecs`)이 이미 RDS 인바운드를 허용받고 있어서, 같은 보안그룹으로 태스크를
띄우면 별도 네트워크 설정 없이 RDS에 붙을 수 있다.

Spring Boot fat jar의 Main-Class를 바꿔서 실행하는 대신(BOOT-INF 중첩 jar 구조 때문에 번거로움),
**컨테이너 시작 시점에 `./gradlew :bali-batch:<커스텀태스크>`를 그대로 실행**하는 이미지를 쓴다.
빌드는 로컬에서 하지만 실행은 컨테이너가 AWS 안에서 뜬 뒤(VPC 네트워크 접근 가능한 시점)
`CMD`가 처음 실행되므로, RDS 접속이 필요한 시점과 정확히 맞아떨어진다.

## 재사용 가능한 조각

### 1. Terraform — ECR 리포지토리 (`infra/terraform/ecr.tf`)

```hcl
resource "aws_ecr_repository" "bali_batch_migration" {
  name                 = "${var.project}-bali-batch-migration"
  image_tag_mutability = "MUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }
}
```

### 2. Terraform — 일회성 태스크 정의 (`infra/terraform/ecs.tf`)

```hcl
resource "aws_cloudwatch_log_group" "bali_batch_migration" {
  name              = "/ecs/${var.project}-bali-batch-migration"
  retention_in_days = 3
}

resource "aws_ecs_task_definition" "bali_batch_migration" {
  family                   = "${var.project}-bali-batch-migration"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "1024"
  memory                   = "2048"
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn

  container_definitions = jsonencode([{
    name      = "bali-batch-migration"
    image     = "${aws_ecr_repository.bali_batch_migration.repository_url}:latest"
    essential = true

    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "dev" },
      { name = "SPRING_DATASOURCE_URL", value = "jdbc:postgresql://${aws_db_instance.main.address}:5432/bali" },
    ]

    secrets = [
      { name = "SPRING_DATASOURCE_USERNAME", valueFrom = "${aws_db_instance.main.master_user_secret[0].secret_arn}:username::" },
      { name = "SPRING_DATASOURCE_PASSWORD", valueFrom = "${aws_db_instance.main.master_user_secret[0].secret_arn}:password::" },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.bali_batch_migration.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "bali-batch-migration"
      }
    }
  }])
}
```

**주의**: env var 이름은 대상 모듈의 `application.yml`을 따라야 한다. `bali-api`는
`DB_URL`/`DB_USERNAME`/`DB_PASSWORD`를 쓰지만 `bali-batch`는
`SPRING_DATASOURCE_URL`/`SPRING_DATASOURCE_USERNAME`/`SPRING_DATASOURCE_PASSWORD`를 쓴다 — 처음에
`bali-api` 쪽 이름을 그대로 갖다 써서 `localhost:5432`로 접속 시도하다 실패했다. 실행 전 대상
모듈의 datasource 설정을 반드시 확인할 것.

### 3. Dockerfile (`bali-batch/Dockerfile.migration`)

```dockerfile
FROM eclipse-temurin:21-jdk
WORKDIR /workspace
COPY . .
CMD ["./gradlew", ":bali-batch:personalArchiveSeed", "--no-daemon", "-x", "test"]
```

`CMD`는 실행할 Gradle `JavaExec` 태스크 이름으로 바꿔 쓴다. 매번 이미지를 다시 안 만들고 싶으면
`aws ecs run-task`의 `--overrides`로 `command`만 바꿔서 여러 스크립트를 재사용할 수 있다 (아래 4번).

### 4. 실행 커맨드

```bash
# 빌드 — 반드시 --platform linux/amd64 (Fargate 기본 아키텍처, M-series Mac에서 빌드하면
# 기본이 arm64라 "exec format error"로 죗는다)
docker build --platform linux/amd64 -f bali-batch/Dockerfile.migration \
  -t <ECR_URL>/bali-dev-bali-batch-migration:latest .

aws ecr get-login-password --region ap-northeast-2 | \
  docker login --username AWS --password-stdin <ECR_URL 앞부분(계정ID.dkr.ecr...)>
docker push <ECR_URL>/bali-dev-bali-batch-migration:latest

# 실행 (기본 CMD 그대로)
aws ecs run-task \
  --cluster bali-dev-cluster \
  --task-definition bali-dev-bali-batch-migration \
  --launch-type FARGATE \
  --network-configuration '{"awsvpcConfiguration":{"subnets":["<퍼블릭서브넷1>","<퍼블릭서브넷2>"],"securityGroups":["<ECS 보안그룹ID>"],"assignPublicIp":"ENABLED"}}'

# 다른 Gradle 태스크를 돌리고 싶으면 command만 오버라이드 (이미지 재빌드 불필요)
aws ecs run-task ... \
  --overrides '{"containerOverrides":[{"name":"bali-batch-migration","command":["./gradlew",":bali-batch:다른태스크","--no-daemon","-x","test"]}]}'
```

퍼블릭 서브넷 + `assignPublicIp=ENABLED` 필수 — NAT 게이트웨이가 없어서 이게 없으면 ECR 이미지
pull 자체가 안 된다.

### 5. 결과 확인 (CloudWatch Logs)

```bash
aws ecs describe-tasks --cluster bali-dev-cluster --tasks <task-arn> \
  --query "tasks[0].{status:lastStatus,exitCode:containers[0].exitCode}"

aws logs describe-log-streams --log-group-name /ecs/bali-dev-bali-batch-migration \
  --order-by LastEventTime --descending --max-items 1 --query "logStreams[0].logStreamName"

aws logs get-log-events --log-group-name /ecs/bali-dev-bali-batch-migration \
  --log-stream-name <위에서 나온 스트림명> --limit 10000 --query "events[*].message" --output text
```

Gradle이 컨테이너 안에서 매번 처음부터 의존성을 내려받고 빌드해서 (레이어 캐시 없음) 완료까지
4~5분 걸린다 — 정상이다.

## 데이터 무결성 관련 주의사항

- **userId를 하드코딩하지 말 것**: `GLOBAL`/`PERSONAL` 종목의 UUID는 환경마다
  `gen_random_uuid()`로 독립 생성돼서 로컬과 dev가 다르다. 계정 UUID도 마찬가지다. 대상 DB에서
  이메일로 `UserRepository.findByEmail(...)`을 호출해 그 자리에서 조회하는 방식으로 짜야 한다.
- **exercise_id를 직접 SQL로 복사하지 말 것**: 위와 같은 이유로 FK가 깨진다. 반드시 이름 기준
  매칭(`ExerciseResolver`류)을 거쳐서, GLOBAL은 매칭시키고 PERSONAL은 새로 만들게 해야 한다.
- **부위(muscleGroup) 힌트를 빠뜨리지 말 것**: 텍스트 재구성 시 `* 부위` 같은 섹션 마커 없이
  종목명만 던지면, 새로 생성되는 PERSONAL 종목이 파서의 기본값(예: `MuscleGroup.BACK`)으로
  전부 잘못 들어간다. 이번에 20개 중 10개가 이 이유로 틀리게 들어가서 사후에 직접 SQL
  `UPDATE`로 보정했다 — 처음부터 부위 정보를 텍스트에 포함시키는 게 낫다.

## 정리(cleanup)

한 번 쓰고 끝낼 마이그레이션이면, ECR에 이미지를 계속 두면 저장 용량만큼 매달 소액이 계속
과금된다(진짜 일회성 비용이 아님). 다 쓰고 나면:

```bash
cd infra/terraform
# ecr.tf/ecs.tf에서 이 문서의 리소스 블록을 지운 뒤
terraform apply   # 3개 리소스 destroy
```

다음에 비슷한 마이그레이션이 또 필요하면 이 문서의 코드 조각을 그대로 다시 붙여넣으면 된다.
