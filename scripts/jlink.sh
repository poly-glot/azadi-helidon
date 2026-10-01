#!/bin/bash
# Build a trimmed JRE for the app: modules found with jdeps + a few that are only loaded reflectively.
set -e
cd "$(dirname "$0")/.."
MODS=$(jdeps --ignore-missing-deps --multi-release 21 --print-module-deps --class-path 'target/libs/*' target/azadi-helidon-*.jar \
  | tr ',' '\n' | grep -v -E '^(jdk\.jdi|jdk\.attach|java\.compiler|java\.instrument|java\.prefs|java\.security\.jgss)$' | paste -sd, -)
EXTRA=jdk.crypto.ec,jdk.unsupported,jdk.zipfs,jdk.httpserver
rm -rf target/jre
jlink --add-modules "$MODS,$EXTRA" --strip-debug --no-header-files --no-man-pages --compress=zip-9 --generate-cds-archive --output target/jre
echo "modules: $MODS,$EXTRA"; du -sm target/jre
