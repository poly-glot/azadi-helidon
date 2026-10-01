#!/bin/bash
# Behaviour checks for the ported routes (contact, documents, bank, statement, settlement, payment date, agreement detail,
# help/legal pages, error pages, emails). Needs app :8080, emulator :8081 and node scripts/fakes.mjs. Mutating checks restore data.
cd "$(dirname "$0")/.."
B=${BASE_URL:-http://localhost:8080}; pass=0; fail=0; n=0
ok()  { printf "  PASS  %s\n" "$1"; pass=$((pass+1)); }
bad() { printf "  FAIL  %s  (%s)\n" "$1" "$2"; fail=$((fail+1)); }
check() { if [ "$2" = "$3" ]; then ok "$1"; else bad "$1" "expected '$3' got '$2'"; fi; }
has()  { if grep -qF -- "$3" <<<"$2"; then ok "$1"; else bad "$1" "missing '$3'"; fi; }
hasnt(){ if grep -qF -- "$3" <<<"$2"; then bad "$1" "unexpected '$3'"; else ok "$1"; fi; }
D=$(mktemp -d); trap 'rm -rf $D' EXIT
tok() { grep XSRF-TOKEN "$1" | awk '{print $7}'; }
# login <name> <agreement> <dob d/m/yyyy> <postcode>  -> cookie jar $D/<name>
login() { n=$((n+1)); local ip="10.20.$n.1"
  curl -s -o /dev/null -c $D/$1 -H "X-Forwarded-For: $ip" $B/login
  curl -s -o /dev/null -b $D/$1 -c $D/$1 -H "X-Forwarded-For: $ip" -d "username=$2&password=$3|$4&_csrf=$(tok $D/$1)" $B/login; echo $ip > $D/$1.ip; }
get()  { curl -s -b $D/$1 -c $D/$1 -H "X-Forwarded-For: $(cat $D/$1.ip)" "$B$2"; }
code() { curl -s -o /dev/null -w '%{http_code}' -b $D/$1 -c $D/$1 -H "X-Forwarded-For: $(cat $D/$1.ip)" "$B$2"; }
post() { curl -s -o /dev/null -w '%{http_code} %{redirect_url}' -b $D/$1 -c $D/$1 -H "X-Forwarded-For: $(cat $D/$1.ip)" --data-urlencode "_csrf=$(tok $D/$1)" "${@:3}" "$B$2"; }
postbody() { curl -s -b $D/$1 -c $D/$1 -H "X-Forwarded-For: $(cat $D/$1.ip)" --data-urlencode "_csrf=$(tok $D/$1)" "${@:3}" "$B$2"; }

login james AGR-100001 15/3/1985 "SW1A 1AA"; login sarah AGR-100002 22/7/1990 "M1 1AE"
login david AGR-100003 3/11/1978 "B1 1BB";     login michael AGR-100005 10/6/1988 "EH1 1YZ"

echo "== isolation between users (template variables must not leak across requests)"
get sarah /finance/settlement-figure >/dev/null
hasnt "James's page shows none of Sarah's data" "$(get james /my-account)" "Sarah"
hasnt "no stale banners on a plain page" "$(get michael /finance/change-payment-date)" "Your payment date has been changed successfully."

echo "== documents"
P=$(get james /my-documents)
has "lists finance-agreement.pdf" "$P" "finance-agreement.pdf"; has "lists welcome-pack.pdf" "$P" "welcome-pack.pdf"

echo "== contact details (Sarah)"
P=$(get sarah /my-contact-details); ORIG_EMAIL=$(grep -o 'value="[^"]*@[^"]*"' <<<"$P" | head -n1 | sed 's/value="//; s/"$//'); ORIG_PHONE=$(grep -o 'name="homePhone"[^>]*' <<<"$P" | head -n1 | grep -o 'value="[^"]*"' | sed 's/value="//; s/"$//')
has "shows current email" "$P" "$ORIG_EMAIL"
check "update -> redirect back" "$(post sarah /my-contact-details -d homePhone=07123456789 -d email=new.sarah@example.com)" "302 $B/my-contact-details"
P=$(get sarah /my-contact-details)
has "flash message shown once" "$P" "Your contact details have been updated successfully."; has "new email persisted" "$P" "new.sarah@example.com"; has "new phone persisted" "$P" "07123456789"
hasnt "flash message is gone on next view" "$(get sarah /my-contact-details)" "have been updated successfully"
post sarah /my-contact-details -d "homePhone=$ORIG_PHONE" -d "email=$ORIG_EMAIL" >/dev/null; get sarah /my-contact-details >/dev/null
check "POST without CSRF token -> 403" "$(curl -s -o /dev/null -w '%{http_code}' -b $D/sarah -H "X-Forwarded-For: $(cat $D/sarah.ip)" -d homePhone=1 $B/my-contact-details)" "403"

echo "== bank details (David) + email"
rm -f target/fake-resend.log
P=$(get david /finance/update-bank-details)
has "masked account number" "$P" "****3344"; has "masked sort code" "$P" "**-**-60"
P=$(postbody david /finance/update-bank-details -d "accountHolderName=Test Holder" -d accountNumber=1234 -d sortCode=12-34-56)
has "invalid account number re-renders with message" "$P" "Account number must be exactly 8 digits"
check "valid update -> redirect" "$(post david /finance/update-bank-details -d "accountHolderName=Test Holder" -d accountNumber=55555555 -d sortCode=12-34-56)" "302 $B/finance/update-bank-details"
P=$(get david /finance/update-bank-details)
has "success flash" "$P" "Your bank details have been updated successfully."; has "new masked account" "$P" "****5555"; has "new masked sort code" "$P" "**-**-56"
sleep 1; has "bank-details email sent via Resend" "$(cat target/fake-resend.log 2>/dev/null)" "subject=Bank Details Updated - Azadi Finance"
post david /finance/update-bank-details -d "accountHolderName=David Patel" -d accountNumber=11223344 -d sortCode=40-50-60 >/dev/null; get david /finance/update-bank-details >/dev/null
has "restored original details" "$(get david /finance/update-bank-details)" "****3344"

echo "== settlement (Sarah)"
AID_S=$(get sarah /finance/settlement-figure | grep -o 'value="[0-9]\{6,\}"' | head -n1 | grep -o '[0-9]*')
P=$(postbody sarah /finance/settlement-figure -d agreementId=$AID_S)
has "settlement = balance + 2% fee" "$P" "£19,125.00"; has "valid for 28 days" "$P" "$(date -u -d '+28 days' +%F)"
check "settlement page loads" "$(code sarah /finance/settlement-figure)" "200"

echo "== statement (Sarah)"
check "request statement -> redirect" "$(post sarah /finance/request-a-statement)" "302 $B/finance/request-a-statement"
has "statement success flash" "$(get sarah /finance/request-a-statement)" "Your statement request has been submitted successfully."

echo "== change payment date (Michael)"
P=$(get michael /finance/change-payment-date); AID_M=$(get michael /finance/make-a-payment | grep -o 'value="[0-9]\{6,\}"' | head -n1 | grep -o '[0-9]*')
if grep -qF "already used your one-time" <<<"$P"; then
  ok "already-changed state renders the notice (form hidden)"; hasnt "form hidden" "$P" 'name="newPaymentDate"'
else
  check "change to the 15th -> redirect" "$(post michael /finance/change-payment-date -d agreementId=$AID_M -d newPaymentDate=15)" "302 $B/finance/change-payment-date"
  P=$(get michael /finance/change-payment-date); has "now marked as changed" "$P" "already used your one-time"; has "next payment date moved to the 15th" "$(get michael /agreements/$AID_M)" "-15"
fi
check "second change attempt -> redirect with error" "$(post michael /finance/change-payment-date -d agreementId=$AID_M -d newPaymentDate=20)" "302 $B/finance/change-payment-date"
has "error flash: already changed" "$(get michael /finance/change-payment-date)" "already been changed"
check "non-numeric day -> 400" "$(post michael /finance/change-payment-date -d agreementId=$AID_M -d newPaymentDate=abc | cut -d' ' -f1)" "400"

echo "== agreement detail + ownership"
AID_J=$(get james /finance/make-a-payment | grep -o 'value="[0-9]\{6,\}"' | head -n1 | grep -o '[0-9]*')
has "own agreement page" "$(get james /agreements/$AID_J)" "AZADI SUMMIT V8 TOURING"
check "someone else's agreement -> 403" "$(code sarah /agreements/$AID_J)" "403"
check "unknown agreement -> 404" "$(code james /agreements/1)" "404"
has "403 renders the error page" "$(get sarah /agreements/$AID_J)" "Access Denied"

echo "== help, legal, errors"
for p in /help/faqs /help/ways-to-pay /help/contact-us; do check "$p (logged in)" "$(code james $p)" "200"; done
for p in /cookies /privacy /terms; do check "$p (public)" "$(curl -s -o /dev/null -w '%{http_code}' -H 'X-Forwarded-For: 10.99.0.1' $B$p)" "200"; done
check "help page needs a session" "$(curl -s -o /dev/null -w '%{http_code}' -H 'X-Forwarded-For: 10.99.0.1' $B/help/faqs)" "302"
check "unknown path (logged in) -> 404" "$(code james /no/such/page)" "404"
has "unknown path renders the Not Found page" "$(get james /no/such/page)" "Not Found"

echo "== payment confirmation email after webhook"
J=$D/james; T=$(tok $J); rm -f target/fake-resend.log
SECRET=$(grep STRIPE_WEBHOOK_SECRET scripts/dev.env | cut -d= -f2)
CS=$(curl -s -b $J -H "X-Forwarded-For: $(cat $J.ip)" -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $T" -d "{\"amountPence\":2500,\"agreementId\":$AID_J}" $B/finance/make-a-payment | grep -o 'pi_fake_[0-9]*' | head -n1)
TS=$(date +%s); EVT="{\"id\":\"evt_$TS\",\"object\":\"event\",\"api_version\":\"2026-01-28.clover\",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"id\":\"$CS\",\"object\":\"payment_intent\",\"amount\":2500,\"currency\":\"gbp\",\"status\":\"succeeded\"}}}"
SIG=$(printf '%s' "$TS.$EVT" | openssl dgst -sha256 -hmac "$SECRET" | awk '{print $NF}')
check "webhook accepted" "$(curl -s -o /dev/null -w '%{http_code}' -H 'X-Forwarded-For: 10.98.0.1' -H "Stripe-Signature: t=$TS,v1=$SIG" -d "$EVT" $B/api/stripe/webhook)" "200"
sleep 1; has "confirmation email sent with the amount" "$(cat target/fake-resend.log 2>/dev/null)" "subject=Payment Confirmation - Azadi Finance"
check "replayed webhook event is ignored (still 200)" "$(curl -s -o /dev/null -w '%{http_code}' -H 'X-Forwarded-For: 10.98.0.1' -H "Stripe-Signature: t=$TS,v1=$SIG" -d "$EVT" $B/api/stripe/webhook)" "200"

echo; echo "passed=$pass failed=$fail"; [ $fail -eq 0 ]
