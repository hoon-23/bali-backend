# 장애 알림: CloudWatch 알람/EventBridge 규칙 → SNS 토픽 → 이메일.
# 비용은 알람당 월 $0.10 수준이고 SNS 이메일 전송은 무료다.

resource "aws_sns_topic" "alerts" {
  name = "${var.project}-alerts"
}

# 구독 후 AWS가 보내는 확인 메일의 링크를 눌러야 알림이 오기 시작한다 (3일 안에 확인하지 않으면 만료)
resource "aws_sns_topic_subscription" "alerts_email" {
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

# EventBridge 규칙(배치 실패)이 토픽에 게시할 수 있도록 허용한다. CloudWatch 알람은 계정 기본 정책으로 게시된다.
resource "aws_sns_topic_policy" "alerts" {
  arn = aws_sns_topic.alerts.arn
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "AllowAccountOwner"
        Effect    = "Allow"
        Principal = { AWS = "*" }
        Action    = ["SNS:Publish", "SNS:Subscribe", "SNS:GetTopicAttributes", "SNS:SetTopicAttributes", "SNS:ListSubscriptionsByTopic", "SNS:DeleteTopic", "SNS:AddPermission", "SNS:RemovePermission", "SNS:Receive"]
        Resource  = aws_sns_topic.alerts.arn
        Condition = { StringEquals = { "AWS:SourceOwner" = data.aws_caller_identity.current.account_id } }
      },
      {
        Sid       = "AllowEventBridgePublish"
        Effect    = "Allow"
        Principal = { Service = "events.amazonaws.com" }
        Action    = "SNS:Publish"
        Resource  = aws_sns_topic.alerts.arn
      },
    ]
  })
}

data "aws_caller_identity" "current" {}

# --- 운영 서버(단일 EC2) 알람 ---

# EC2 상태 점검(인스턴스/시스템)이 연속 실패하면 알린다
resource "aws_cloudwatch_metric_alarm" "app_status_check" {
  alarm_name          = "${var.project}-app-status-check-failed"
  alarm_description   = "운영 EC2 상태 점검 실패 (인스턴스 또는 시스템)"
  namespace           = "AWS/EC2"
  metric_name         = "StatusCheckFailed"
  dimensions          = { InstanceId = aws_instance.app.id }
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 3
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "breaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

# t4g는 버스트형이라 CPU 크레딧이 바닥나면 느려지거나(unlimited 모드는 추가 요금) 한다. 30 미만이 15분 지속되면 알린다
resource "aws_cloudwatch_metric_alarm" "app_cpu_credit_low" {
  alarm_name          = "${var.project}-app-cpu-credit-low"
  alarm_description   = "운영 EC2 CPU 크레딧 잔액 30 미만 (15분 지속)"
  namespace           = "AWS/EC2"
  metric_name         = "CPUCreditBalance"
  dimensions          = { InstanceId = aws_instance.app.id }
  statistic           = "Minimum"
  period              = 300
  evaluation_periods  = 3
  threshold           = 30
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}
