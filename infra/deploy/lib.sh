# 공용 함수: 실패 시 SNS 알림 (CloudWatch 알람과 같은 토픽 -> 이메일)
REGION=ap-northeast-2
notify() {
  local account topic
  account=$(aws sts get-caller-identity --query Account --output text)
  topic="arn:aws:sns:$REGION:$account:bali-dev-alerts"
  aws sns publish --region $REGION --topic-arn "$topic" --subject "$1" --message "$2" >/dev/null || true
}
