#!/bin/sh
# docker-entrypoint.sh — writes config.json from env vars, then launches the service.
# Environment variables (with defaults):
#   DATABASE_URL      jdbc:postgresql://postgres:5432/appbana
#   DATABASE_USER     appbana
#   DATABASE_PASSWORD appbana_dev_2026
#   AI_PROVIDER       openai
#   OPENAI_API_KEY    (required when AI_PROVIDER=openai)
#   OPENAI_MODEL      gpt-4o-mini
#   APPBANA_PORT      8080
set -e

cat > /app/config.json << CONF
{
  "jdbcUrl": "${DATABASE_URL:-jdbc:postgresql://postgres:5432/appbana}",
  "username": "${DATABASE_USER:-appbana}",
  "password": "${DATABASE_PASSWORD:-appbana_dev_2026}",
  "driver": "org.postgresql.Driver",
  "name": "default",
  "aiProvider": "${AI_PROVIDER:-openai}",
  "openaiApiKey": "${OPENAI_API_KEY:-}",
  "openaiModel": "${OPENAI_MODEL:-gpt-4o-mini}",
  "flywayCleanOnStart": false
}
CONF

exec java \
  -Dappbana.port="${APPBANA_PORT:-8080}" \
  -jar /app/app-bana-service.jar
