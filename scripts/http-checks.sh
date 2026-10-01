#!/bin/bash
# HTTP-level behaviour checks for the security/session/payment rewrite. Needs the app on :8080, emulator on :8081,
# fakes on :12111/:12112 (node scripts/fakes.mjs). Uses X-Forwarded-For to get a distinct client IP per group.
cd "$(dirname "$0")/.."
B=${BASE_URL:-http://localhost:8080}; pass=0; fail=0
ok()   { printf "  PASS  %s\n" "$1"; pass=$((pass+1)); }
bad()  { printf "  FAIL  %s  (%s)\n" "$1" "$2"; fail=$((fail+1)); }
check() { if [ "$2" = "$3" ]; then ok "$1"; else bad "$1" "expected '$3' got '$2'"; fi; }
has()  { if grep -qi -- "$3" <<<"$2"; then ok "$1"; else bad "$1" "missing '$3'"; fi; }
J=$(mktemp); trap 'rm -f $J $J.*' EXIT
xsrf() { grep XSRF-TOKEN "$1" | awk '{print $7}'; }
creds() { echo "username=$1&password=$2"; }

echo "== headers, CSRF cookie, auth guard"
curl -s -D $J.h -o $J.b -c $J -H "X-Forwarded-For: 10.0.0.1" $B/login; H=$(cat $J.h); BODY=$(cat $J.b)
has "login page 200" "$H" "^HTTP/1.1 200"
has "CSP with nonce"  "$H" "content-security-policy: default-src 'self'; script-src 'self' 'nonce-"
has "HSTS"            "$H" "strict-transport-security"
has "X-Frame-Options" "$H" "x-frame-options: DENY"
has "nosniff"         "$H" "x-content-type-options: nosniff"
has "XSRF cookie set" "$H" "set-cookie: XSRF-TOKEN="
NONCE=$(grep -io "nonce-[A-Za-z0-9+/_-]*" <<<"$H" | head -n1 | cut -d- -f2-)
has "inline script carries the header's nonce" "$BODY" "nonce=\"$NONCE\""
check "protected page redirects to login" "$(curl -s -o /dev/null -w '%{http_code} %{redirect_url}' -H 'X-Forwarded-For: 10.0.0.1' $B/my-account)" "302 $B/login"
check "static asset is public" "$(curl -s -o /dev/null -w '%{http_code}' -H 'X-Forwarded-For: 10.0.0.1' $B/assets/img/logo-azadi.svg)" "200"
ETAG=$(curl -s -D - -o /dev/null -H 'X-Forwarded-For: 10.0.0.1' $B/assets/img/logo-azadi.svg | grep -i '^etag:' | awk '{print $2}' | tr -d '\r')
check "conditional GET on a static asset -> 304 (not 500)" "$(curl -s -o /dev/null -w '%{http_code}' -H 'X-Forwarded-For: 10.0.0.1' -H "If-None-Match: $ETAG" $B/assets/img/logo-azadi.svg)" "304"
has "health is public" "$(curl -s -H 'X-Forwarded-For: 10.0.0.1' $B/actuator/health)" '"status":"UP"'
T=$(xsrf $J)
check "POST /login without CSRF token -> 403" "$(curl -s -o /dev/null -w '%{http_code}' -b $J -H 'X-Forwarded-For: 10.0.0.1' -d "$(creds AGR-100001 '15/3/1985|SW1A 1AA')" $B/login)" "403"
check "POST /login with wrong CSRF token -> 403" "$(curl -s -o /dev/null -w '%{http_code}' -b $J -H 'X-Forwarded-For: 10.0.0.1' -d "$(creds AGR-100001 '15/3/1985|SW1A 1AA')&_csrf=nope" $B/login)" "403"

echo "== login, session cookie, fixation, logout"
check "wrong postcode -> login-error" "$(curl -s -o /dev/null -w '%{redirect_url}' -b $J -H 'X-Forwarded-For: 10.0.0.2' -d "$(creds AGR-100001 '15/3/1985|ZZ1 1ZZ')&_csrf=$T" $B/login)" "$B/login-error"
R=$(curl -s -D - -o /dev/null -b $J -c $J.s -H 'X-Forwarded-For: 10.0.0.2' -d "$(creds AGR-100001 '15/3/1985|sw1a1aa')&_csrf=$T" $B/login)
has "correct login redirects to /my-account" "$R" "location: /my-account"
has "session cookie HttpOnly + SameSite=Strict" "$R" "set-cookie: __session=[A-Za-z0-9_-]*; Path=/; HttpOnly; SameSite=Strict"
check "XSRF token rotated on login" "$([ "$(xsrf $J.s)" != "$T" ] && echo rotated)" "rotated"
SID=$(grep __session $J.s | awk '{print $7}')
PAGE=$(curl -s -b $J.s -H 'X-Forwarded-For: 10.0.0.2' $B/my-account)
has "my-account shows the customer" "$PAGE" "James Wilson"
has "my-account shows agreement data" "$PAGE" "AZADI SUMMIT V8 TOURING"
check "forged session id is rejected" "$(curl -s -o /dev/null -w '%{http_code}' -H 'Cookie: __session=forged' -H 'X-Forwarded-For: 10.0.0.2' $B/my-account)" "302"
T2=$(xsrf $J.s)
R=$(curl -s -D - -o /dev/null -b $J.s -H 'X-Forwarded-For: 10.0.0.2' -d "_csrf=$T2" $B/logout)
has "logout clears the cookie" "$R" "__session=; Expires=Thu, 1 Jan 1970 00:00:00 GMT; Path=/; HttpOnly; SameSite=Strict"
check "old session is dead after logout" "$(curl -s -o /dev/null -w '%{http_code}' -H "Cookie: __session=$SID" -H 'X-Forwarded-For: 10.0.0.2' $B/my-account)" "302"

echo "== lockout (5 bad attempts on one agreement, different client IPs)"
for i in 1 2 3 4 5; do curl -s -o /dev/null -b $J -H "X-Forwarded-For: 10.1.0.$i" -d "$(creds AGR-100004 '1/1/1990|BAD')&_csrf=$T" $B/login; done
check "6th attempt with CORRECT credentials is still refused" "$(curl -s -o /dev/null -w '%{redirect_url}' -b $J -H 'X-Forwarded-For: 10.1.0.9' -d "$(creds AGR-100004 '28/1/1992|LS1 1BA')&_csrf=$T" $B/login)" "$B/login-error"

echo "== per-IP login rate limit"
for i in 1 2 3 4 5; do curl -s -o /dev/null -b $J -H "X-Forwarded-For: 10.2.0.1" -d "$(creds AGR-999999 'x|y')&_csrf=$T" $B/login; done
check "6th login POST from one IP -> 429" "$(curl -s -o /dev/null -w '%{http_code}' -b $J -H 'X-Forwarded-For: 10.2.0.1' -d "$(creds AGR-999999 'x|y')&_csrf=$T" $B/login)" "429"

echo "== payments (fake Stripe on :12111)"
curl -s -o /dev/null -b $J -c $J.p -H 'X-Forwarded-For: 10.3.0.1' $B/login
T3=$(xsrf $J.p)
curl -s -o /dev/null -b $J.p -c $J.p -H 'X-Forwarded-For: 10.3.0.1' -d "$(creds AGR-100001 '15/3/1985|SW1A 1AA')&_csrf=$T3" $B/login
T3=$(xsrf $J.p)
AID=$(curl -s -b $J.p -H 'X-Forwarded-For: 10.3.0.1' $B/finance/make-a-payment | grep -o 'value="[0-9]\{6,\}"' | head -n1 | grep -o '[0-9]*')
check "payment page renders the agreement select" "$([ -n "$AID" ] && echo yes)" "yes"
check "payment POST without CSRF header -> 403" "$(curl -s -o /dev/null -w '%{http_code}' -H 'X-Forwarded-For: 10.3.0.1' -H 'Content-Type: application/json' -d "{\"amountPence\":1000,\"agreementId\":$AID}" $B/finance/make-a-payment)" "403"
check "amount below minimum is rejected" "$(curl -s -o /dev/null -w '%{http_code}' -b $J.p -H 'X-Forwarded-For: 10.3.0.1' -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $T3" -d "{\"amountPence\":50,\"agreementId\":$AID}" $B/finance/make-a-payment)" "400"
R=$(curl -s -b $J.p -H 'X-Forwarded-For: 10.3.0.1' -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $T3" -d "{\"amountPence\":1000,\"agreementId\":$AID}" $B/finance/make-a-payment)
has "payment POST (frontend's X-CSRF-TOKEN header) returns a client secret" "$R" '"clientSecret":"pi_fake_[0-9]*_secret_fake"'
has "Stripe SDK sent amount+metadata to the (fake) Stripe API" "$(tail -n 5 target/fake-stripe.log)" "amount=1000"

echo "== webhook signature"
SECRET=whsec_test_secret; TS=$(date +%s)
EVT='{"id":"evt_test_1","object":"event","api_version":"2026-01-28.clover","type":"payment_intent.succeeded","data":{"object":{"id":"pi_fake_1","object":"payment_intent","amount":1000,"currency":"gbp","status":"succeeded"}}}'
SIG=$(printf '%s' "$TS.$EVT" | openssl dgst -sha256 -hmac "$SECRET" | awk '{print $NF}')
check "webhook with bad signature -> 400" "$(curl -s -o /dev/null -w '%{http_code}' -H 'X-Forwarded-For: 10.4.0.1' -H "Stripe-Signature: t=$TS,v1=deadbeef" -d "$EVT" $B/api/stripe/webhook)" "400"
check "webhook with valid signature -> 200" "$(curl -s -o /dev/null -w '%{http_code}' -H 'X-Forwarded-For: 10.4.0.1' -H "Stripe-Signature: t=$TS,v1=$SIG" -d "$EVT" $B/api/stripe/webhook)" "200"

if [ -z "$SKIP_RATE_LIMIT" ]; then
echo "== general rate limit"
for i in $(seq 1 60); do curl -s -o /dev/null -H 'X-Forwarded-For: 10.5.0.1' $B/actuator/health; done
check "61st request in a minute -> 429" "$(curl -s -o /dev/null -w '%{http_code}' -H 'X-Forwarded-For: 10.5.0.1' $B/actuator/health)" "429"
fi

echo; echo "passed=$pass failed=$fail"; [ $fail -eq 0 ]
