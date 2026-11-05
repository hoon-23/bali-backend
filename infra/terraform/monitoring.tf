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

# ALB 뒤 앱(타겟)이 5분간 5xx를 5건 이상 내면 알린다
resource "aws_cloudwatch_metric_alarm" "alb_target_5xx" {
  alarm_name          = "${var.project}-alb-target-5xx"
  alarm_description   = "bali-api가 5분간 5xx를 5건 이상 반환"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "HTTPCode_Target_5XX_Count"
  dimensions          = { LoadBalancer = aws_lb.main.arn_suffix }
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 5
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

# 정상 타겟이 3분 연속 0개면(태스크 다운/기동 실패) 알린다. 배포 중에는 새 태스크가 뜬 뒤 기존 태스크가 내려가므로 오탐하지 않는다
resource "aws_cloudwatch_metric_alarm" "api_no_healthy_host" {
  alarm_name        = "${var.project}-api-no-healthy-host"
  alarm_description = "bali-api 정상 타겟이 3분 연속 0개"
  namespace         = "AWS/ApplicationELB"
  metric_name       = "HealthyHostCount"
  dimensions = {
    LoadBalancer = aws_lb.main.arn_suffix
    TargetGroup  = aws_lb_target_group.bali_api.arn_suffix
  }
  statistic           = "Minimum"
  period              = 60
  evaluation_periods  = 3
  threshold           = 1
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

# 배치 태스크가 0이 아닌 종료 코드로 끝나면(= 러너가 1을 반환) 알린다
resource "aws_cloudwatch_event_rule" "batch_task_failed" {
  name        = "${var.project}-batch-task-failed"
  description = "bali-batch ECS 태스크가 비정상 종료"

  event_pattern = jsonencode({
    source        = ["aws.ecs"]
    "detail-type" = ["ECS Task State Change"]
    detail = {
      clusterArn        = [aws_ecs_cluster.main.arn]
      lastStatus        = ["STOPPED"]
      taskDefinitionArn = [{ prefix = "arn:aws:ecs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:task-definition/${var.project}-bali-batch:" }]
      containers        = { exitCode = [{ "anything-but" = 0 }] }
    }
  })
}

resource "aws_cloudwatch_event_target" "batch_task_failed" {
  rule      = aws_cloudwatch_event_rule.batch_task_failed.name
  target_id = "sns"
  arn       = aws_sns_topic.alerts.arn

  # 원본 이벤트 JSON 대신 사람이 읽을 수 있는 한 줄 메시지로 보낸다
  input_transformer {
    input_paths    = { task = "$.detail.taskArn", reason = "$.detail.stoppedReason", time = "$.time" }
    input_template = "\"[bali-batch 실패] <time> 태스크 <task> 비정상 종료: <reason>\""
  }
}
