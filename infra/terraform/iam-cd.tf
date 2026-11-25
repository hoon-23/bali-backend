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
        Resource = [
          aws_ecr_repository.bali_api.arn,
          aws_ecr_repository.bali_batch.arn,
        ]
      },
      {
        Sid      = "DeployFilesSync"
        Effect   = "Allow"
        Action   = ["s3:PutObject", "s3:DeleteObject", "s3:GetObject"]
        Resource = "${aws_s3_bucket.db_backup.arn}/deploy/*"
      },
      {
        Sid      = "DeployFilesList"
        Effect   = "Allow"
        Action   = ["s3:ListBucket"]
        Resource = aws_s3_bucket.db_backup.arn
      },
      {
        Sid      = "SsmRunOnAppInstance"
        Effect   = "Allow"
        Action   = ["ssm:SendCommand"]
        Resource = [aws_instance.app.arn, "arn:aws:ssm:${var.aws_region}::document/AWS-RunShellScript"]
      },
      {
        Sid      = "SsmReadResult"
        Effect   = "Allow"
        Action   = ["ssm:GetCommandInvocation", "ssm:ListCommandInvocations"]
        Resource = "*"
      },
      {
        Sid      = "FindAppInstance"
        Effect   = "Allow"
        Action   = ["ec2:DescribeInstances"]
        Resource = "*"
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
