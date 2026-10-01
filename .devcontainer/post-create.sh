#!/usr/bin/env bash
set -euo pipefail

echo "=== Azadi Helidon Dev Container Setup ==="

sudo chown vscode:vscode /home/vscode/.m2 /home/vscode/.npm 2>/dev/null || true

git config --global --add safe.directory /workspace
git config --global init.defaultBranch main
git config --global alias.st status
git config --global alias.co checkout
git config --global alias.ci commit

cat >> ~/.zshrc << 'ALIASES'

alias claude="claude --dangerously-skip-permissions"

alias fb-emulator="gcloud emulators firestore start --host-port=0.0.0.0:8081 --database-mode=datastore-mode --project=demo-azadi"
alias fb-emulator-reset="kill \$(lsof -ti:8081) 2>/dev/null; sleep 1; fb-emulator"
alias build-jar="cd /workspace && mvn -B package -DskipTests"
alias seed="cd /workspace && ./scripts/seed.sh"
alias dev="cd /workspace && mvn -B -q package -DskipTests && ./scripts/run.sh"
alias fakes="cd /workspace && node scripts/fakes.mjs"
alias checks="cd /workspace && ./scripts/http-checks.sh && ./scripts/route-checks.sh"

alias dc='docker compose'
alias dcup='docker compose up -d'
alias dcdown='docker compose down'

ALIASES

[ -f ~/.bashrc ] && ! grep -q 'exec zsh' ~/.bashrc && echo '[ -t 1 ] && exec zsh' >> ~/.bashrc

echo "Resolving Maven dependencies..."
(cd /workspace && mvn -B -q dependency:go-offline > /tmp/maven-install.log 2>&1 && echo "Maven deps: OK" || echo "Maven deps: FAILED")

echo "=== Setup complete ==="
echo ""
echo "Quick start (3 terminals):"
echo "  1. fb-emulator   -> Firestore emulator (:8081)"
echo "  2. fakes         -> fake Stripe / Resend / GCE metadata"
echo "  3. seed && dev   -> seed 5 demo customers, build and run on :8080"
echo "  -> Open http://localhost:8080"
echo ""
echo "  checks           -> HTTP + route checks against the running app"
echo "  build-jar        -> Build the app jar"
echo ""
