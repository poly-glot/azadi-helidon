#!/bin/bash
cd "$(dirname "$0")/.."
set -a; . scripts/dev.env; set +a
exec java -cp "target/azadi-helidon-0.1.0-SNAPSHOT.jar:target/libs/*" guru.junaid.azadi.seed.DataSeeder
