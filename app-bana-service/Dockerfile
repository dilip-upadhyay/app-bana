# app-bana-service/Dockerfile
# Build from the repository root:
#   docker build -f app-bana-service/Dockerfile -t app-bana-service .

# ─── Build stage ─────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy parent POM first so Maven can resolve the module hierarchy.
# The parent pom.xml declares both modules; provide a stub ai-builder/pom.xml so
# the reactor can be read without the ai-builder source being present.
COPY pom.xml ./
COPY app-bana-service/pom.xml ./app-bana-service/
RUN mkdir -p ai-builder && printf '<project><modelVersion>4.0.0</modelVersion><parent><groupId>com.appbana</groupId><artifactId>app-bana-parent</artifactId><version>1.0-SNAPSHOT</version><relativePath>../pom.xml</relativePath></parent><artifactId>ai-builder</artifactId></project>' > ai-builder/pom.xml

# Pre-fetch dependencies for better layer caching
RUN mvn -pl app-bana-service dependency:go-offline -q 2>/dev/null || true

# Copy the source tree
COPY app-bana-service/src ./app-bana-service/src

# Build the fat JAR (maven-shade-plugin produces *-fat.jar)
RUN mvn -pl app-bana-service package -DskipTests -q

# ─── Runtime stage ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /build/app-bana-service/target/app-bana-1.0-SNAPSHOT-fat.jar app-bana-service.jar
COPY app-bana-service/docker-entrypoint.sh ./entrypoint.sh
RUN chmod +x ./entrypoint.sh

EXPOSE 8080

ENTRYPOINT ["./entrypoint.sh"]
