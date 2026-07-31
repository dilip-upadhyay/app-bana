# ai-builder/Dockerfile
# Build from the repository root:
#   docker build -f ai-builder/Dockerfile -t ai-builder .

# ─── Build stage ─────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy parent POM first so Maven can resolve the module hierarchy.
# The parent pom.xml declares both modules; provide a stub app-bana-service/pom.xml so
# the reactor can be read without the app-bana-service source being present.
COPY pom.xml ./
COPY ai-builder/pom.xml ./ai-builder/
RUN mkdir -p app-bana-service && printf '<project><modelVersion>4.0.0</modelVersion><parent><groupId>com.appbana</groupId><artifactId>app-bana-parent</artifactId><version>1.0-SNAPSHOT</version><relativePath>../pom.xml</relativePath></parent><artifactId>app-bana</artifactId></project>' > app-bana-service/pom.xml

# Pre-fetch dependencies for better layer caching
RUN mvn -pl ai-builder dependency:go-offline -q 2>/dev/null || true

# Copy the source tree
COPY ai-builder/src ./ai-builder/src

# Build the fat JAR (maven-shade-plugin produces *-fat.jar)
RUN mvn -pl ai-builder package -DskipTests -q

# ─── Runtime stage ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /build/ai-builder/target/ai-builder-1.0-SNAPSHOT-fat.jar ai-builder.jar

EXPOSE 8081

# AiConfig reads all settings from environment variables — no config file needed.
ENTRYPOINT ["java", "-jar", "/app/ai-builder.jar"]
