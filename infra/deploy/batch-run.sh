#!/bin/bash
# bali-batch 잡 한 개를 실행한다 (EventBridge 스케줄을 대체하는 호스트 cron용). 사용법: batch-run.sh <job>
# state/batch-enabled 파일이 없으면 아무것도 하지 않는다. 컷오버 전에 EventBridge와 중복 실행(푸시 중복)되는 것을 막기 위함이다.
set -uo pipefail
source "$(dirname "$0")/lib.sh"
JOB=${1:?job name}
cd /opt/bali
[ -f state/batch-enabled ] || { echo "batch 비활성: $JOB 건너뜀"; exit 0; }

ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
IMAGE="$ACCOUNT.dkr.ecr.$REGION.amazonaws.com/bali-dev-bali-batch:$(cat state/batch-tag)"
DB_PASSWORD=$(grep '^DB_PASSWORD=' .env | cut -d= -f2-)

# 메모리 상한: api(블루/그린 전환 시 2개)와 Postgres가 함께 떠 있어도 OOM이 나지 않도록 제한한다
if docker run --rm --network bali --memory 512m -e JAVA_TOOL_OPTIONS="-Xmx384m" \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/bali \
  -e SPRING_DATASOURCE_USERNAME=bali -e SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
  "$IMAGE" "$JOB"; then
  echo "$(date '+%F %T') 배치 완료: $JOB"
else
  CODE=$?
  notify "[bali] 배치 실패: $JOB" "bali-batch $JOB 가 종료 코드 $CODE 로 실패했습니다 ($(date '+%F %T'))"
  exit $CODE
fi
