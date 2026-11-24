#!/bin/bash
# 5분마다 실행: API 응답, 디스크, 메모리를 점검해 이상이 생기면 SNS로 알린다 (ALB 헬스체크 알람의 대체).
# 같은 이상이 계속되어도 메일이 반복되지 않도록 상태가 바뀔 때(이상 발생/복구)만 알린다.
# FORCE_FAIL=1 로 실행하면 이상 상황을 흉내 내 알림 경로를 시험할 수 있다.
set -uo pipefail
source "$(dirname "$0")/lib.sh"
cd /opt/bali; mkdir -p state/monitor

# 이름, 이상 여부(0=정상 1=이상), 메시지
check() {
  local name=$1 bad=$2 msg=$3 f=state/monitor/$1
  if [ "$bad" = 1 ] && [ ! -f "$f" ]; then touch "$f"; notify "[bali] 이상: $name" "$msg"
  elif [ "$bad" = 0 ] && [ -f "$f" ]; then rm -f "$f"; notify "[bali] 복구: $name" "$name 이(가) 정상으로 돌아왔습니다"; fi
}

# API: nginx를 거쳐 헬스체크. 2번 연속 실패해야 이상으로 본다 (배포 전환 순간의 오탐 방지)
H=$(sed -n 's/.*"\(.*\)".*/\1/p' nginx/dyn/origin-verify.conf | head -1)
ok() { curl -fs -m 5 -H "X-Origin-Verify: $H" localhost/actuator/health | grep -q '"status":"UP"'; }
if [ "${FORCE_FAIL:-0}" = 1 ]; then api_bad=1; elif ok || { sleep 10; ok; }; then api_bad=0; else api_bad=1; fi
check api "$api_bad" "API 헬스체크가 연속 실패했습니다 ($(date '+%F %T'))"

# 디스크: 루트 사용률 80% 이상
DISK=$(df / --output=pcent | tail -1 | tr -dc '0-9')
check disk $([ "$DISK" -ge 80 ] && echo 1 || echo 0) "디스크 사용률 ${DISK}%"

# 메모리: 사용 가능 메모리 200MB 미만 (스왑 포함 아님)
AVAIL=$(free -m | awk 'NR==2{print $7}')
check memory $([ "$AVAIL" -lt 200 ] && echo 1 || echo 0) "사용 가능 메모리 ${AVAIL}MB"
