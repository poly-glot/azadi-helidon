#!/bin/bash
# Run on the jlink runtime with low-footprint JVM flags. Override with JOPTS.
cd "$(dirname "$0")/.."
JOPTS=${JOPTS:--XX:+UseSerialGC -Xss512k -Xmx64m -Xms16m -XX:MaxMetaspaceSize=64m -XX:ReservedCodeCacheSize=24m -XX:TieredStopAtLevel=1 -XX:CICompilerCount=1 -XX:-UsePerfData}
exec target/jre/bin/java $JOPTS -cp "target/azadi-helidon-0.1.0-SNAPSHOT.jar:target/libs/*" guru.junaid.azadi.Main
