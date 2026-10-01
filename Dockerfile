# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /src
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -P no-checks package -DskipTests
RUN MODS=$(jdeps --ignore-missing-deps --multi-release 21 --print-module-deps --class-path 'target/libs/*' target/azadi-helidon-*.jar \
      | tr ',' '\n' | grep -v -E '^(jdk\.jdi|jdk\.attach|java\.compiler|java\.instrument|java\.prefs|java\.security\.jgss)$' | paste -sd, -) \
 && echo "modules: $MODS" \
 && jlink --add-modules "$MODS,jdk.crypto.ec,jdk.unsupported,jdk.zipfs,jdk.httpserver" \
      --strip-debug --no-header-files --no-man-pages --compress=zip-9 --generate-cds-archive --output /opt/jre \
 && rm /opt/jre/lib/server/classes_nocoops.jsa /opt/jre/lib/libawt*.so /opt/jre/lib/libfontmanager.so /opt/jre/lib/libfreetype.so \
       /opt/jre/lib/liblcms.so /opt/jre/lib/libmlib_image.so /opt/jre/lib/libjavajpeg.so /opt/jre/lib/libjsound.so /opt/jre/lib/libjawt.so \
       /opt/jre/lib/psfont* \
 && apk add --no-cache binutils && strip --strip-unneeded /opt/jre/lib/server/libjvm.so /opt/jre/lib/*.so

FROM alpine:3.21 AS base
RUN adduser -S -D -H -u 10001 -s /sbin/nologin azadi
WORKDIR /app
COPY --from=build /opt/jre /opt/jre
COPY --from=build /src/target/libs /app/libs
COPY --from=build /src/target/azadi-helidon-0.1.0-SNAPSHOT.jar /app/app.jar

FROM base AS train
RUN /opt/jre/bin/java -cp "/app/app.jar:/app/libs/*" guru.junaid.azadi.Train /app/app.jsa /app/app.jar \
      -XX:+UseSerialGC -Xss512k -Xmx32m -Xms16m -XX:MaxMetaspaceSize=48m -XX:ReservedCodeCacheSize=20m \
      -XX:TieredStopAtLevel=1 -XX:CICompilerCount=1 -XX:-UsePerfData

FROM base AS runtime
COPY --from=train /app/app.jsa /app/app.jsa
ENV PORT=8080
USER 10001
EXPOSE 8080
ENTRYPOINT ["/opt/jre/bin/java", "-XX:+UseSerialGC", "-Xss512k", "-Xmx32m", "-Xms16m", "-XX:MinHeapFreeRatio=10", "-XX:MaxHeapFreeRatio=20", \
            "-XX:MaxMetaspaceSize=48m", "-XX:ReservedCodeCacheSize=20m", "-XX:TieredStopAtLevel=1", "-XX:CICompilerCount=1", \
            "-XX:-UsePerfData", "-XX:SharedArchiveFile=/app/app.jsa", "-jar", "/app/app.jar"]
