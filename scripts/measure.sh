#!/bin/bash
# Start the app, exercise it in stages, print RSS after each stage. Extra stages can be added below.
cd "$(dirname "$0")/.."
LOG=${LOG:-target/run.log}
./scripts/run.sh > "$LOG" 2>&1 &
pid=$!
for i in $(seq 1 120); do grep -q "started on port" "$LOG" && break; grep -q "Exception" "$LOG" && break; sleep 0.5; done
rss() { printf "%-34s RSS %6.1f MB (anon %5.1f, file %5.1f)\n" "$1" "$(awk '/VmRSS/{print $2/1024}' /proc/$pid/status)" "$(awk '/RssAnon/{print $2/1024}' /proc/$pid/status)" "$(awk '/RssFile/{print $2/1024}' /proc/$pid/status)"; }
grep -E "seeded|started" "$LOG"
rss "after startup"
curl -s -o /dev/null localhost:8080/ping;                       rss "after /ping (helidon only)"
curl -s -o /dev/null -w "  /login=%{http_code}\n" localhost:8080/login;  rss "after /login (thymeleaf)"
curl -s -o /dev/null -w "  /my-account=%{http_code}\n" localhost:8080/my-account; rss "after /my-account (+datastore)"
curl -s -m 20 localhost:8080/stripe | cut -c1-90;               rss "after /stripe (+stripe sdk)"
for i in $(seq 1 300); do curl -s -o /dev/null localhost:8080/my-account; curl -s -o /dev/null localhost:8080/login; done
rss "after 600 more requests"
[ -n "$KEEP" ] || kill $pid
