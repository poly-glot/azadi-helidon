#!/bin/bash
# Full-application memory measurement on the jlink runtime with the tuned flags.
#  1. trains the AppCDS archive on the real workload (HTTP checks + Chrome journey), stopping with SIGTERM
#  2. starts the app with the archive and prints RSS after: startup, HTTP checks, Chrome journeys, sustained load
cd "$(dirname "$0")/.."
FLAGS="${FLAGS:--XX:+UseSerialGC -Xss512k -Xmx32m -Xms16m -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=20 -XX:MaxMetaspaceSize=48m -XX:ReservedCodeCacheSize=20m -XX:TieredStopAtLevel=1 -XX:CICompilerCount=1 -XX:-UsePerfData}"
CP="target/azadi-helidon-0.1.0-SNAPSHOT.jar:target/libs/*"
port_pid() { ss -ltnp 2>/dev/null | grep ":8080 " | grep -o 'pid=[0-9]*' | head -n1 | cut -d= -f2; }
start() { # $1 = extra JVM flags
  ( set -a; . scripts/dev.env; set +a; export MALLOC_ARENA_MAX=2 RATE_LIMIT_GENERAL=100000
    setsid nohup target/jre/bin/java $FLAGS $1 -cp "$CP" guru.junaid.azadi.Main > target/run.log 2>&1 < /dev/null & )
  for i in $(seq 1 80); do grep -q "started on port" target/run.log && break; sleep 0.5; done; PID=$(port_pid)
}
alive() { [ -r /proc/$PID/status ] && ! grep -q "^State:.*Z" /proc/$PID/status; }  # orphans may linger as zombies
stop() { local t0=$(date +%s.%N); kill -TERM $PID; while alive; do sleep 0.1; done
  printf "  SIGTERM -> process exited after %.1fs\n" "$(awk -v a=$t0 -v b=$(date +%s.%N) 'BEGIN{print b-a}')"; }
rss() { printf "%-40s RSS %6.1f MB (anon %5.1f, file %5.1f)\n" "$1" $(awk '/VmRSS/{print $2/1024}' /proc/$PID/status) $(awk '/RssAnon/{print $2/1024}' /proc/$PID/status) $(awk '/RssFile/{print $2/1024}' /proc/$PID/status); }

if [ ! -f target/app.jsa ] || [ -n "$RETRAIN" ]; then
  echo "== training AppCDS archive"; rm -f target/app.jsa
  start "-XX:ArchiveClassesAtExit=target/app.jsa"
  export SKIP_RATE_LIMIT=1
  ./scripts/http-checks.sh > /dev/null 2>&1; ./scripts/route-checks.sh > /dev/null 2>&1
  node scripts/chrome-journey.mjs > /dev/null 2>&1; node scripts/chrome-pages.mjs > /dev/null 2>&1
  stop; sleep 3; ls -la target/app.jsa | awk '{printf "  archive %.1f MB\n", $5/1048576}'
fi
echo "== measuring"
start "-XX:SharedArchiveFile=target/app.jsa"
export SKIP_RATE_LIMIT=1
rss "idle after startup"
./scripts/http-checks.sh > /dev/null 2>&1;                       rss "after HTTP checks (32)"
./scripts/route-checks.sh > /dev/null 2>&1;                      rss "after route checks (46, all ported routes)"
node scripts/chrome-journey.mjs > /dev/null 2>&1; node scripts/chrome-pages.mjs > /dev/null 2>&1; rss "after Chrome journey + all 15 pages"
J=$(mktemp); curl -s -o /dev/null -c $J -H "X-Forwarded-For: 10.7.0.1" localhost:8080/login; T=$(grep XSRF $J | awk '{print $7}')
curl -s -o /dev/null -b $J -c $J -H "X-Forwarded-For: 10.7.0.1" -d "username=AGR-100001&password=15/3/1985|SW1A 1AA&_csrf=$T" localhost:8080/login
for i in $(seq 1 1500); do curl -s -o /dev/null -b $J -H "X-Forwarded-For: 10.7.0.1" localhost:8080/my-account; curl -s -o /dev/null -b $J -H "X-Forwarded-For: 10.7.0.1" localhost:8080/finance/make-a-payment; curl -s -o /dev/null -H "X-Forwarded-For: 10.7.0.$((i%200+2))" localhost:8080/login; done
rss "after +4500 requests (auth pages+login)"
for i in $(seq 1 300); do for p in /my-documents /my-contact-details /finance/settlement-figure /finance/update-bank-details /help/faqs; do curl -s -o /dev/null -b $J -H "X-Forwarded-For: 10.7.0.1" localhost:8080$p; done; done; rss "after +1500 requests on the other routes"
grep -c "OutOfMemory" target/run.log | sed 's/^/  OutOfMemoryError count: /'
stop
