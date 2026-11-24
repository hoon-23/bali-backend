#!/bin/bash
# RDS의 데이터를 이 EC2의 Postgres 컨테이너로 옮긴다 (이전 리허설과 컷오버에서 같은 절차를 쓴다).
# 사용법: migrate-from-rds.sh <RDS 엔드포인트> <RDS 마스터 시크릿 ARN> <대상 DB 이름>
# 대상 DB가 이미 있으면 지우고 다시 만든다. 끝나면 양쪽의 테이블별 행 수를 출력해 비교할 수 있게 한다.
set -euo pipefail
HOST=${1:?rds endpoint}; SECRET=${2:?master secret arn}; TARGET=${3:?target db}
cd /opt/bali

CRED=$(aws secretsmanager get-secret-value --region ap-northeast-2 --secret-id "$SECRET" --query SecretString --output text)
RUSER=$(echo "$CRED" | jq -r .username); RPASS=$(echo "$CRED" | jq -r .password)
PG="docker run --rm --network bali -e PGPASSWORD=$RPASS postgres:16"

COUNT_SQL="SELECT table_name||' '||(xpath('/row/c/text()', query_to_xml(format('select count(*) as c from %I.%I', table_schema, table_name), false, true, '')))[1]::text FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' ORDER BY 1"

echo "== RDS 덤프"
$PG pg_dump -h "$HOST" -U "$RUSER" -d bali -Fc --no-owner --no-privileges > /opt/bali/rds.dump
ls -l /opt/bali/rds.dump

echo "== 대상 DB 재생성: $TARGET"
docker exec bali-postgres-1 psql -U bali -d postgres -c "DROP DATABASE IF EXISTS \"$TARGET\" WITH (FORCE)" -c "CREATE DATABASE \"$TARGET\" OWNER bali"
docker exec -i bali-postgres-1 pg_restore -U bali -d "$TARGET" --no-owner --no-privileges < /opt/bali/rds.dump

echo "== 행 수 비교 (RDS / 대상)"
$PG psql -h "$HOST" -U "$RUSER" -d bali -At -c "$COUNT_SQL" > /tmp/counts.rds
docker exec bali-postgres-1 psql -U bali -d "$TARGET" -At -c "$COUNT_SQL" > /tmp/counts.target
if diff /tmp/counts.rds /tmp/counts.target; then echo "행 수 일치 ($(wc -l < /tmp/counts.rds) 개 테이블)"; else echo "행 수 불일치!" >&2; exit 1; fi
