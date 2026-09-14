# bali-batch: Airflow 없이 EventBridge Scheduler가 ECS RunTask를 직접 트리거하는 구조.
# bali-batch는 원래부터 "웹서버 없이 뜨고 종료코드만 반환하는 순수 실행형"으로 설계돼 있어(로컬 DockerOperator와 동일 이미지)
# 별도 오케스트레이터 없이도 command override만으로 잡을 고를 수 있다. weekly→weekly-summary-push,
# monthly→monthly-summary-push처럼 순서가 있는 잡은 태스크 체이닝 대신 몇 분 간격을 둔 별도 스케줄로 근사한다
# (개인 프로젝트 규모라 배치가 몇 분 안에 끝나서 레이스 컨디션 리스크가 낮다고 판단).

resource "aws_ecr_repository" "bali_batch" {
  name                 = "${var.project}-bali-batch"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "bali_batch" {
  repository = aws_ecr_repository.bali_batch.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "최근 10개 이미지만 보관"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}

resource "aws_cloudwatch_log_group" "bali_batch" {
  name              = "/ecs/${var.project}-bali-batch"
  retention_in_days = 14
}

# 실행 역할은 bali-api와 동일한 것을 재사용한다 — 이미 ECR pull + DB 시크릿 read 권한을 갖고 있고,
# bali-batch가 필요한 권한도 정확히 그 부분집합이다(별도 롤을 만들면 동일한 정책을 중복 정의하게 됨)
resource "aws_ecs_task_definition" "bali_batch" {
  family                   = "${var.project}-bali-batch"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "256"
  memory                   = "512"
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn

  container_definitions = jsonencode([{
    name      = "bali-batch"
    image     = "${aws_ecr_repository.bali_batch.repository_url}:latest"
    essential = true

    environment = [
      { name = "SPRING_DATASOURCE_URL", value = "jdbc:postgresql://${aws_db_instance.main.address}:5432/bali" },
    ]

    secrets = [
      { name = "SPRING_DATASOURCE_USERNAME", valueFrom = "${aws_db_instance.main.master_user_secret[0].secret_arn}:username::" },
      { name = "SPRING_DATASOURCE_PASSWORD", valueFrom = "${aws_db_instance.main.master_user_secret[0].secret_arn}:password::" },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.bali_batch.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "bali-batch"
      }
    }
  }])
}

# EventBridge Scheduler가 ECS RunTask를 호출할 때 assume하는 롤 (ecs:RunTask + 태스크가 쓰는 실행 롤에 대한 PassRole)
resource "aws_iam_role" "eventbridge_scheduler" {
  name = "${var.project}-eventbridge-scheduler-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "scheduler.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "eventbridge_scheduler_run_task" {
  name = "${var.project}-eventbridge-scheduler-run-task"
  role = aws_iam_role.eventbridge_scheduler.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "RunTask"
        Effect   = "Allow"
        Action   = ["ecs:RunTask"]
        Resource = replace(aws_ecs_task_definition.bali_batch.arn, "/:[0-9]+$/", ":*")
      },
      {
        Sid      = "PassExecutionRole"
        Effect   = "Allow"
        Action   = ["iam:PassRole"]
        Resource = [aws_iam_role.ecs_task_execution.arn]
      },
    ]
  })
}

# 배치 잡별 스케줄. cron은 앱 코드와 동일하게 Asia/Seoul 로컬시간 기준으로 지정해 UTC 환산 실수를 피한다.
locals {
  bali_batch_schedules = {
    weekly = {
      cron = "cron(0 0 ? * MON *)"
    }
    weekly-summary-push = {
      cron = "cron(10 0 ? * MON *)" # weekly 완료를 기다리는 10분 오프셋
    }
    monthly = {
      cron = "cron(0 0 1 * ? *)"
    }
    monthly-summary-push = {
      cron = "cron(10 0 1 * ? *)" # monthly 완료를 기다리는 10분 오프셋
    }
    inactivity-alert = {
      cron = "cron(0 0 * * ? *)"
    }
    routine-reminder = {
      cron = "cron(0 6,18 * * ? *)"
    }
  }
}

resource "aws_scheduler_schedule" "bali_batch" {
  for_each = local.bali_batch_schedules

  name                         = "${var.project}-batch-${each.key}"
  schedule_expression          = each.value.cron
  schedule_expression_timezone = "Asia/Seoul"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = aws_ecs_cluster.main.arn
    role_arn = aws_iam_role.eventbridge_scheduler.arn

    ecs_parameters {
      task_definition_arn = aws_ecs_task_definition.bali_batch.arn
      launch_type         = "FARGATE"

      network_configuration {
        subnets          = aws_subnet.public[*].id
        security_groups  = [aws_security_group.ecs.id]
        assign_public_ip = true
      }
    }

    input = jsonencode({
      containerOverrides = [{
        name    = "bali-batch"
        command = [each.key]
      }]
    })
  }
}
