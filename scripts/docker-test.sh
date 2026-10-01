#!/bin/bash
# Runs the built image against the emulator + fakes on this dev container's network and verifies it end to end:
# archive really used (-Xshare:on), HTTP/route suites, Chrome on the Mac (published port), memory, startup, SIGTERM.
# usage: scripts/docker-test.sh [image] [extra docker run args...]
cd "$(dirname "$0")/.."
IMAGE=${1:-azadi-helidon:test}; shift
NET=$(docker inspect "$(hostname)" --format '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}{{end}}' 2>/dev/null)
ME=$(hostname -i 2>/dev/null | awk '{print $1}')
if [ -z "$NET" ]; then ME=host.docker.internal; NETWORK=""; else NETWORK="--network $NET"; fi
NAME=azadi-test; PUBLISH=18080
docker rm -f $NAME >/dev/null 2>&1
set -a; . scripts/dev.env; set +a
t0=$(date +%s.%N)
docker run -d --name $NAME $NETWORK -p $PUBLISH:8080 "$@" \
  -e JAVA_TOOL_OPTIONS=-Xshare:on \
  -e DATASTORE_HOST=$ME:8081 -e GCP_PROJECT_ID -e COOKIE_SECURE=false -e DEMO_MODE=true -e RATE_LIMIT_GENERAL=100000 \
  -e STRIPE_API_KEY -e STRIPE_WEBHOOK_SECRET -e VITE_STRIPE_PUBLISHABLE_KEY -e STRIPE_API_BASE=http://$ME:12111 \
  -e AZADI_ENCRYPTION_KEY -e AZADI_ENCRYPTION_SALT -e RESEND_API_KEY -e RESEND_API_URL=http://$ME:12113/emails \
  "$IMAGE" >/dev/null
if [ -n "$NET" ]; then CIP=$(docker inspect -f "{{.NetworkSettings.Networks.$NET.IPAddress}}" $NAME); export BASE_URL=http://$CIP:8080; else CIP=localhost; export BASE_URL=http://localhost:$PUBLISH; fi
for i in $(seq 1 200); do [ "$(curl -s -o /dev/null -w '%{http_code}' $BASE_URL/login)" = 200 ] && break; sleep 0.05; done
printf "cold start: docker run -> first /login 200 in %.2fs (container %s)\n" "$(awk -v a=$t0 -v b=$(date +%s.%N) 'BEGIN{print b-a}')" "$CIP"
docker logs $NAME 2>&1 | grep -E "Picked up|crypto round-trip|started on port|shared|Error" | head -n 5
rss() { printf "%-34s RSS %6.1f MB  | docker stats: %s\n" "$1" "$(docker exec $NAME awk '/VmRSS/{print $2/1024}' /proc/1/status)" "$(docker stats --no-stream --format '{{.MemUsage}}' $NAME)"; }
rss "idle after startup"
export SKIP_RATE_LIMIT=1
echo "--- http-checks";  ./scripts/http-checks.sh  2>&1 | grep -E "FAIL|passed="
echo "--- route-checks"; ./scripts/route-checks.sh 2>&1 | grep -E "FAIL|passed="
rss "after 78 checks"
echo "--- chrome (Mac) via published port $PUBLISH"
BASE=http://localhost:$PUBLISH node scripts/chrome-journey.mjs 2>&1 | grep -c '"issues":\[\]' | sed 's/^/  journey steps without issues: /'
BASE=http://localhost:$PUBLISH node scripts/chrome-pages.mjs   2>&1 | grep -E '"issues":\["' | grep -v "/no/such/page" | cut -c1-200
rss "after Chrome journey + 15 pages"
J=$(mktemp); curl -s -o /dev/null -c $J -H "X-Forwarded-For: 10.7.0.1" $BASE_URL/login; T=$(grep XSRF $J | awk '{print $7}')
curl -s -o /dev/null -b $J -c $J -H "X-Forwarded-For: 10.7.0.1" -d "username=AGR-100001&password=15/3/1985|SW1A 1AA&_csrf=$T" $BASE_URL/login
for i in $(seq 1 1000); do for p in /my-account /finance/make-a-payment /my-documents /help/faqs; do curl -s -o /dev/null -b $J -H "X-Forwarded-For: 10.7.0.1" $BASE_URL$p; done; done
rss "after +4000 requests"
docker logs $NAME 2>&1 | grep -c -E "OutOfMemory" | sed 's/^/  OutOfMemoryError count: /'
s0=$(date +%s.%N); docker stop $NAME >/dev/null; printf "docker stop (SIGTERM) took %.1fs, exit code %s\n" "$(awk -v a=$s0 -v b=$(date +%s.%N) 'BEGIN{print b-a}')" "$(docker inspect -f '{{.State.ExitCode}}' $NAME)"
docker rm $NAME >/dev/null
