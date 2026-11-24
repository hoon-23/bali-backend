#!/bin/bash
# 블루/그린 무중단 배포: 반대 색상으로 새 버전을 띄우고, 헬스체크가 통과하면 nginx를 전환한 뒤 이전 버전을 내린다.
# 사용법: deploy.sh <image-tag> [batch-image-tag]   (종료 코드 0 = 전환 성공, 1 = 전환 안 함·기존 버전 유지)
set -euo pipefail
TAG=${1:?image tag required}
BATCH_TAG=${2:-}   # 선택: 배치 이미지 태그 (주면 이 태그로 갱신)
REGION=ap-northeast-2
cd /opt/bali

# 배포가 겹쳐 실행되면 blue/green 상태가 꼬이므로 하나씩만 실행한다 (뒤 실행은 앞 실행이 끝날 때까지 대기)
exec 9>/var/lock/bali-deploy.lock
flock 9

./fetch-env.sh
# 백업/배치 cron을 최신 정의로 갱신한다
install -m 644 bali.cron /etc/cron.d/bali
ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
IMAGE="$ACCOUNT.dkr.ecr.$REGION.amazonaws.com/bali-dev-bali-api:$TAG"

ACTIVE=$(cat state/active 2>/dev/null || echo none)
if [ "$ACTIVE" = blue ]; then NEW=green; NEW_PORT=8082; else NEW=blue; NEW_PORT=8081; fi
mkdir -p state nginx/dyn

docker compose --env-file .env up -d postgres

if [ -n "$BATCH_TAG" ]; then
  BIMAGE="$ACCOUNT.dkr.ecr.$REGION.amazonaws.com/bali-dev-bali-batch:$BATCH_TAG"
  docker image inspect "$BIMAGE" >/dev/null 2>&1 || docker pull "$BIMAGE"
  echo "$BATCH_TAG" > state/batch-tag
fi
# 로컬에 이미 있는 이미지(리허설용 빌드)는 pull하지 않는다
docker image inspect "$IMAGE" >/dev/null 2>&1 || docker pull "$IMAGE"

docker rm -f "bali-api-$NEW" >/dev/null 2>&1 || true
# 힙 상한과 컨테이너 메모리 상한: 4GB 인스턴스에서 JVM 2개가 동시에 떠도 버티도록 제한한다
docker run -d --name "bali-api-$NEW" --network bali --restart unless-stopped \
  --memory 1g --env-file .env -e JAVA_TOOL_OPTIONS="-Xmx768m" \
  -p "127.0.0.1:$NEW_PORT:8080" "$IMAGE" >/dev/null

# 기동에 65~75초가 걸리므로 최대 180초까지 기다린다
for i in $(seq 1 36); do
  if curl -fs "http://127.0.0.1:$NEW_PORT/actuator/health" | grep -q '"status":"UP"'; then OK=1; break; fi
  sleep 5
done
if [ -z "${OK:-}" ]; then
  echo "헬스체크 실패: $NEW 컨테이너를 제거하고 기존 버전($ACTIVE)을 유지합니다" >&2
  docker logs --tail 30 "bali-api-$NEW" >&2 || true
  docker rm -f "bali-api-$NEW" >/dev/null
  exit 1
fi

echo "upstream bali_api { server bali-api-$NEW:8080; }" > nginx/dyn/active-upstream.conf
if docker ps --format '{{.Names}}' | grep -qx bali-nginx-1; then
  docker exec bali-nginx-1 nginx -s reload
else
  docker compose --env-file .env up -d nginx
fi
echo "$NEW" > state/active

# 진행 중이던 요청이 끝나도록 잠시 두었다가 이전 버전을 내린다
if [ "$ACTIVE" != none ]; then
  sleep 30
  docker rm -f "bali-api-$ACTIVE" >/dev/null 2>&1 || true
fi
echo "배포 완료: $NEW ($TAG)"
