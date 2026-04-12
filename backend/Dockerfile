# syntax=docker/dockerfile:1.6
FROM gradle:8.10-jdk21-alpine AS builder
WORKDIR /src

# 의존성 캐시 레이어
COPY settings.gradle build.gradle gradle.properties lombok.config ./
RUN gradle dependencies --no-daemon --refresh-dependencies || true

# 소스 복사 후 빌드
COPY src ./src
RUN gradle bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S app && adduser -S app -G app \
    && apk add --no-cache curl
WORKDIR /app
COPY --from=builder --chown=app:app /src/build/libs/*.jar /app/app.jar

USER app
EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75 -Dspring.profiles.active=prod"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
