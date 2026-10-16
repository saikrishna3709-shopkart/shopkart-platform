# ---- build stage ----
FROM maven:3.9.11-eclipse-temurin-21 AS builder

WORKDIR /build
COPY pom.xml .
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

# ---- runtime stage ----
FROM eclipse-temurin:21-jre-jammy

RUN groupadd --gid 10001 appuser \
    && useradd --uid 10001 --gid appuser --no-create-home --shell /usr/sbin/nologin appuser

WORKDIR /app
COPY --from=builder --chown=10001:10001 /build/target/inventory-service-*.jar /app/app.jar

USER 10001

EXPOSE 8000
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
