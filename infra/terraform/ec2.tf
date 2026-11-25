# 단일 EC2 스택: nginx(블루/그린 전환) + bali-api 컨테이너 + Postgres 컨테이너 + 호스트 cron 배치.
# 컷오버 전까지는 기존 ECS/ALB/RDS와 병행하며, 앱 서비스 트래픽은 CloudFront origin을 바꾸기 전엔 받지 않는다.

data "aws_ami" "app" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023*-arm64"]
  }
}

resource "aws_security_group" "app" {
  name        = "${var.project}-app-sg"
  description = "Allow 80 from CloudFront only, no SSH (SSM only)"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "CloudFront origin-facing"
    from_port       = 80
    to_port         = 80
    protocol        = "tcp"
    prefix_list_ids = [data.aws_ec2_managed_prefix_list.cloudfront.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# 컨테이너 안의 Postgres 비밀번호. 컷오버 때 이 값으로 새 DB를 초기화한다
resource "random_password" "app_db" {
  length  = 32
  special = false
}

resource "aws_secretsmanager_secret" "app_db_password" {
  name = "${var.project}/app-db-password"
}

resource "aws_secretsmanager_secret_version" "app_db_password" {
  secret_id     = aws_secretsmanager_secret.app_db_password.id
  secret_string = random_password.app_db.result
}

# nginx가 검증할 CloudFront 헤더 값. ALB 리스너 규칙과 같은 값(random_password.origin_verify)을 EC2가 읽도록 공유한다
resource "aws_secretsmanager_secret" "origin_verify" {
  name = "${var.project}/origin-verify"
}

resource "aws_secretsmanager_secret_version" "origin_verify" {
  secret_id     = aws_secretsmanager_secret.origin_verify.id
  secret_string = random_password.origin_verify.result
}

# 네이버 로그인 인가 코드 교환용 client id/secret. 값은 Terraform에 넣지 않고 사용자가 직접 설정한다(Apple 시크릿과 같은 방식).
# 값이 비어 있으면 서버는 코드 로그인을 설정 오류로 거부하고, 구버전 앱의 access token 방식은 그대로 동작한다
resource "aws_secretsmanager_secret" "naver_client_id" {
  name = "${var.project}/naver-client-id"
}

resource "aws_secretsmanager_secret" "naver_client_secret" {
  name = "${var.project}/naver-client-secret"
}

# DB 덤프 보관용 버킷 (일 단위 백업, 30일 후 삭제)
resource "aws_s3_bucket" "db_backup" {
  bucket = "${var.project}-db-backup"
}

resource "aws_s3_bucket_public_access_block" "db_backup" {
  bucket                  = aws_s3_bucket.db_backup.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "db_backup" {
  bucket = aws_s3_bucket.db_backup.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_versioning" "db_backup" {
  bucket = aws_s3_bucket.db_backup.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "db_backup" {
  bucket = aws_s3_bucket.db_backup.id

  rule {
    id     = "expire-old-dumps"
    status = "Enabled"
    filter {}
    expiration {
      days = 30
    }
    noncurrent_version_expiration {
      noncurrent_days = 30
    }
  }
}

resource "aws_iam_role" "app" {
  name = "${var.project}-app-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "app_ssm" {
  role       = aws_iam_role.app.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy_attachment" "app_ecr_read" {
  role       = aws_iam_role.app.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

resource "aws_iam_role_policy" "app" {
  name = "${var.project}-app-access"
  role = aws_iam_role.app.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = ["secretsmanager:GetSecretValue"]
        Resource = [
          aws_secretsmanager_secret.jwt_secret.arn,
          aws_secretsmanager_secret.google_client_id.arn,
          aws_secretsmanager_secret.apple_bundle_id.arn,
          aws_secretsmanager_secret.kakao_client_id.arn,
          aws_secretsmanager_secret.apple_team_id.arn,
          aws_secretsmanager_secret.apple_key_id.arn,
          aws_secretsmanager_secret.apple_private_key.arn,
          aws_secretsmanager_secret.app_db_password.arn,
          aws_secretsmanager_secret.origin_verify.arn,
          aws_secretsmanager_secret.naver_client_id.arn,
          aws_secretsmanager_secret.naver_client_secret.arn,
        ]
      },
      {
        Effect   = "Allow"
        Action   = ["s3:PutObject", "s3:GetObject", "s3:ListBucket"]
        Resource = [aws_s3_bucket.db_backup.arn, "${aws_s3_bucket.db_backup.arn}/*"]
      },
      {
        Effect   = "Allow"
        Action   = ["sns:Publish"]
        Resource = aws_sns_topic.alerts.arn
      },
    ]
  })
}

resource "aws_iam_instance_profile" "app" {
  name = "${var.project}-app-profile"
  role = aws_iam_role.app.name
}

resource "aws_instance" "app" {
  ami                    = data.aws_ami.app.id
  instance_type          = "t4g.medium"
  subnet_id              = aws_subnet.public[0].id
  vpc_security_group_ids = [aws_security_group.app.id]
  iam_instance_profile   = aws_iam_instance_profile.app.name

  # 종료 보호: 실수로 terminate해 DB 볼륨이 사라지는 것을 막는다
  disable_api_termination = true

  root_block_device {
    volume_type = "gp3"
    volume_size = 30
    encrypted   = true
  }

  # 도커 설치, 스왑 2GB(메모리 여유 확보), 시간대 KST, 작업 디렉터리 준비. 앱 기동은 deploy.sh가 담당한다
  user_data = <<-EOT
    #!/bin/bash
    set -euo pipefail
    dnf install -y docker cronie jq
    systemctl enable --now docker crond
    mkdir -p /usr/local/lib/docker/cli-plugins
    curl -fsSL "https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-aarch64" -o /usr/local/lib/docker/cli-plugins/docker-compose
    chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
    timedatectl set-timezone Asia/Seoul
    fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
    echo '/swapfile none swap sw 0 0' >> /etc/fstab
    mkdir -p /opt/bali/pgdata /opt/bali/nginx
  EOT

  tags = {
    Name = "${var.project}-app"
  }

  # 새 AMI가 나와도 운영 중인 인스턴스를 교체하지 않는다 (bastion과 동일한 이유)
  lifecycle {
    ignore_changes = [ami, user_data]
  }
}

resource "aws_eip" "app" {
  instance = aws_instance.app.id
  domain   = "vpc"
  tags     = { Name = "${var.project}-app-eip" }
}


# CloudFront origin-facing 접두사 목록. 서버 보안 그룹이 80 포트를 CloudFront에서만 받도록 쓴다
data "aws_ec2_managed_prefix_list" "cloudfront" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

# CloudFront가 origin 요청마다 붙이는 비밀 헤더 값. 서버의 nginx가 이 값을 검증해 직접 접근을 막는다
resource "random_password" "origin_verify" {
  length  = 40
  special = false
}
