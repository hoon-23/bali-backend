# bali-batch 이미지 저장소. 배치는 서버의 호스트 cron이 이 이미지를 받아 실행한다(infra/deploy/batch-run.sh, ADR-0004).
# 예전에는 EventBridge Scheduler + ECS RunTask로 실행했고 2026-11-24 단일 EC2 이전으로 제거했다.

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
