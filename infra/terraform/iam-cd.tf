resource "aws_iam_user" "cd" {
  name = "${var.project}-cd"
}

resource "aws_iam_access_key" "cd" {
  user = aws_iam_user.cd.name
}

resource "aws_iam_user_policy" "cd" {
  name = "${var.project}-cd-policy"
  user = aws_iam_user.cd.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "EcrAuth"
        Effect   = "Allow"
        Action   = ["ecr:GetAuthorizationToken"]
        Resource = "*"
      },
      {
        Sid    = "EcrPush"
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchGetImage",
          "ecr:PutImage",
          "ecr:InitiateLayerUpload",
          "ecr:UploadLayerPart",
          "ecr:CompleteLayerUpload",
        ]
        Resource = aws_ecr_repository.bali_api.arn
      },
      {
        Sid    = "EcsDeploy"
        Effect = "Allow"
        Action = [
          "ecs:UpdateService",
          "ecs:DescribeServices",
        ]
        Resource = aws_ecs_service.bali_api.id
      },
    ]
  })
}

output "cd_access_key_id" {
  value = aws_iam_access_key.cd.id
}

output "cd_secret_access_key" {
  value     = aws_iam_access_key.cd.secret
  sensitive = true
}
