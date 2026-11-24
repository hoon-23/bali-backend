#!/bin/bash
# Secrets Manager에서 값을 읽어 /opt/bali/.env 와 nginx 헤더 검증 파일을 만들고 ECR에 로그인한다.
set -euo pipefail
REGION=ap-northeast-2
P=bali-dev
sec() { aws secretsmanager get-secret-value --region $REGION --secret-id "$P/$1" --query SecretString --output text; }

umask 077
cd /opt/bali
{
  echo "SPRING_PROFILES_ACTIVE=dev"
  echo "DB_URL=jdbc:postgresql://postgres:5432/bali"
  echo "DB_USERNAME=bali"
  echo "DB_PASSWORD=$(sec app-db-password)"
  echo "JWT_SECRET=$(sec jwt-secret)"
  echo "GOOGLE_CLIENT_ID=$(sec google-client-id)"
  echo "APPLE_BUNDLE_ID=$(sec apple-bundle-id)"
  echo "KAKAO_CLIENT_ID=$(sec kakao-client-id)"
  echo "APPLE_TEAM_ID=$(sec apple-team-id)"
  echo "APPLE_KEY_ID=$(sec apple-key-id)"
  # 개행이 들어 있는 PEM 키는 env 파일에서 한 줄로 깨지므로 \n 이스케이프 형태로 저장한다
  echo "APPLE_PRIVATE_KEY=$(sec apple-private-key | awk 'BEGIN{ORS="\\n"} {print}')"
} > .env.new
mv .env.new .env

mkdir -p nginx/dyn
printf 'if ($http_x_origin_verify != "%s") { return 403; }\n' "$(sec origin-verify)" > nginx/dyn/origin-verify.conf

ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
aws ecr get-login-password --region $REGION | docker login --username AWS --password-stdin "$ACCOUNT.dkr.ecr.$REGION.amazonaws.com" >/dev/null
