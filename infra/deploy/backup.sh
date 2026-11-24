#!/bin/bash
# 매일 pg_dump(-Fc)를 S3에 올린다. RDS 자동 백업이 없어지므로 이것이 유일한 백업이다. 실패하면 SNS로 알린다.
set -uo pipefail
source "$(dirname "$0")/lib.sh"
BUCKET=bali-dev-db-backup
DBNAME=${DBNAME:-bali}
KEY="daily/$(date +%Y%m%d-%H%M).dump"

if docker exec bali-postgres-1 pg_dump -U bali -Fc "$DBNAME" | aws s3 cp - "s3://$BUCKET/$KEY" --region $REGION; then
  # 빈 파일이 올라가는 것을 걸러낸다 (덤프가 실패해도 업로드는 성공할 수 있음)
  SIZE=$(aws s3api head-object --bucket $BUCKET --key "$KEY" --query ContentLength --output text)
  if [ "${SIZE:-0}" -lt 1024 ]; then
    notify "[bali] DB 백업 이상" "백업 파일이 비정상적으로 작습니다 ($KEY, ${SIZE} bytes)"
    exit 1
  fi
  echo "백업 완료: $KEY ($SIZE bytes)"
else
  notify "[bali] DB 백업 실패" "pg_dump 또는 S3 업로드가 실패했습니다 ($KEY)"
  exit 1
fi
