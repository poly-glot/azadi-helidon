#!/bin/bash
# Floor measurement: bare Helidon (Ping) on a Helidon-only classpath, same JVM flags as run.sh.
cd "$(dirname "$0")/.."
mkdir -p target/libs-helidon
cp target/libs/helidon-*.jar target/libs/jakarta*.jar target/libs-helidon/ 2>/dev/null
export MALLOC_ARENA_MAX=2
target/jre/bin/java -XX:+UseSerialGC -Xss512k -XX:MaxMetaspaceSize=64m -XX:ReservedCodeCacheSize=24m -XX:TieredStopAtLevel=1 -XX:CICompilerCount=1 -XX:-UsePerfData -Xmx40m -Xms16m -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=20 \
  -cp "target/azadi-helidon-0.1.0-SNAPSHOT.jar:target/libs-helidon/*" guru.junaid.azadi.Ping > target/ping.log 2>&1 &
pid=$!
for i in $(seq 1 40); do grep -q started target/ping.log && break; sleep 0.5; done
r() { printf "%-30s RSS %5.1f MB (anon %5.1f, file %5.1f) threads=%s\n" "$1" $(awk '/VmRSS/{print $2/1024}' /proc/$pid/status) $(awk '/RssAnon/{print $2/1024}' /proc/$pid/status) $(awk '/RssFile/{print $2/1024}' /proc/$pid/status) $(ls /proc/$pid/task | wc -l); }
echo "helidon jars: $(ls target/libs-helidon | wc -l)"
r "helidon-only startup"; for i in $(seq 1 500); do curl -s -o /dev/null localhost:8080/ping; done; r "helidon-only after 500 req"
kill $pid
