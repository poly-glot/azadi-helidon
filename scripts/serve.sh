#!/bin/bash
# Run the app detached with the tuned flags (uses target/app.jsa when present). Logs to target/run.log.
cd "$(dirname "$0")/.."
export MALLOC_ARENA_MAX=2
set -a; . scripts/dev.env; set +a
CDS=""; [ -f target/app.jsa ] && CDS="-XX:SharedArchiveFile=target/app.jsa"
setsid nohup target/jre/bin/java -XX:+UseSerialGC -Xss512k -Xmx32m -Xms16m -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=20 -XX:MaxMetaspaceSize=48m -XX:ReservedCodeCacheSize=20m -XX:TieredStopAtLevel=1 -XX:CICompilerCount=1 -XX:-UsePerfData $CDS \
  -cp "target/azadi-helidon-0.1.0-SNAPSHOT.jar:target/libs/*" guru.junaid.azadi.Main > target/run.log 2>&1 < /dev/null &
for i in $(seq 1 60); do grep -q "started on port" target/run.log && break; sleep 0.5; done
grep -E "started" target/run.log
