# Build stage: compile the server with Gradle (works on ARM and x86).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --no-daemon -q dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon -q installDist -x test

# Run stage: just the Java runtime and the built server.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/build/install/isaipetti-social /app
ENV DB_PATH=/data/isaipetti-social.db
VOLUME /data
EXPOSE 8095
ENTRYPOINT ["/app/bin/isaipetti-social"]
