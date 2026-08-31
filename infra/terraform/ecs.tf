resource "aws_ecs_cluster" "main" {
  name = "${var.project}-cluster"
}

resource "aws_security_group" "ecs" {
  name        = "${var.project}-ecs-sg"
  description = "Allow 8080 inbound only from the ALB"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# RDS 보안그룹에 ECS → RDS 인바운드 허용 규칙을 별도로 추가 (순환 참조 방지를 위해 rds.tf가 아닌 여기서 정의)
resource "aws_security_group_rule" "rds_from_ecs" {
  type                     = "ingress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  security_group_id        = aws_security_group.rds.id
  source_security_group_id = aws_security_group.ecs.id
}

resource "aws_iam_role" "ecs_task_execution" {
  name = "${var.project}-ecs-task-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_task_execution" {
  role       = aws_iam_role.ecs_task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# 태스크 실행 역할이 Secrets Manager 값을 읽을 수 있어야 컨테이너 기동 시 시크릿 주입이 된다
resource "aws_iam_role_policy" "ecs_secrets_access" {
  name = "${var.project}-ecs-secrets-access"
  role = aws_iam_role.ecs_task_execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = ["secretsmanager:GetSecretValue"]
      Resource = [
        aws_secretsmanager_secret.jwt_secret.arn,
        aws_secretsmanager_secret.google_client_id.arn,
        aws_secretsmanager_secret.apple_bundle_id.arn,
        aws_secretsmanager_secret.kakao_client_id.arn,
        aws_db_instance.main.master_user_secret[0].secret_arn,
      ]
    }]
  })
}

resource "aws_cloudwatch_log_group" "bali_api" {
  name              = "/ecs/${var.project}-bali-api"
  retention_in_days = 14
}

resource "aws_ecs_task_definition" "bali_api" {
  family                   = "${var.project}-bali-api"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn

  container_definitions = jsonencode([{
    name      = "bali-api"
    image     = "${aws_ecr_repository.bali_api.repository_url}:latest"
    essential = true

    portMappings = [{ containerPort = 8080, protocol = "tcp" }]

    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "dev" },
      { name = "DB_URL", value = "jdbc:postgresql://${aws_db_instance.main.address}:5432/bali" },
    ]

    secrets = [
      { name = "JWT_SECRET", valueFrom = aws_secretsmanager_secret.jwt_secret.arn },
      { name = "GOOGLE_CLIENT_ID", valueFrom = aws_secretsmanager_secret.google_client_id.arn },
      { name = "APPLE_BUNDLE_ID", valueFrom = aws_secretsmanager_secret.apple_bundle_id.arn },
      { name = "KAKAO_CLIENT_ID", valueFrom = aws_secretsmanager_secret.kakao_client_id.arn },
      { name = "DB_USERNAME", valueFrom = "${aws_db_instance.main.master_user_secret[0].secret_arn}:username::" },
      { name = "DB_PASSWORD", valueFrom = "${aws_db_instance.main.master_user_secret[0].secret_arn}:password::" },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.bali_api.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "bali-api"
      }
    }
  }])
}

resource "aws_ecs_service" "bali_api" {
  name            = "${var.project}-bali-api"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.bali_api.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.bali_api.arn
    container_name    = "bali-api"
    container_port    = 8080
  }

  depends_on = [aws_iam_role_policy_attachment.ecs_task_execution]

  # desired_count는 비용 절감을 위해 AWS CLI로 수동 조정한다(안 쓸 때 0으로 내림) — Terraform이 다시 1로 되돌리지 않도록 무시
  lifecycle {
    ignore_changes = [desired_count]
  }
}
