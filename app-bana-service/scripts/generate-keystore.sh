#!/usr/bin/env bash
# Minimal keystore generator for AppBana HTTPS (self-signed, PKCS12)
#
# Usage (zsh/bash):
#   ./scripts/generate-keystore.sh
#
# This will create certs/keystore.p12 with alias 'appbana' and password 'changeit'.
# For production, use a real certificate and keep secrets safe.

set -euo pipefail

CERTS_DIR="certs"
KEYSTORE_PATH="$CERTS_DIR/keystore.p12"
ALIAS="appbana"
STOREPASS="changeit"
KEYPASS="changeit"
DNAME="CN=localhost, OU=Dev, O=AppBana, L=Local, S=Local, C=US"

mkdir -p "$CERTS_DIR"

if command -v keytool >/dev/null 2>&1; then
  echo "Generating PKCS12 keystore at $KEYSTORE_PATH ..."
  keytool -genkeypair \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -storetype PKCS12 \
    -keystore "$KEYSTORE_PATH" \
    -storepass "$STOREPASS" \
    -keypass "$KEYPASS" \
    -dname "$DNAME"
  echo "Done. Keystore: $KEYSTORE_PATH"
  echo "Use these env vars (example):"
  echo "  APPBANA_HTTPS_ENABLED=true \\"
  echo "  APPBANA_KEYSTORE_PATH=$KEYSTORE_PATH \\"
  echo "  APPBANA_KEYSTORE_PASSWORD=$STOREPASS \\"
  echo "  APPBANA_KEY_PASSWORD=$KEYPASS \\"
  echo "  APPBANA_HTTPS_PORT=8443 \\"
  echo "  APPBANA_REDIRECT_HTTP_TO_HTTPS=true \\"
  echo "  java -jar target/app-bana-1.0-SNAPSHOT-fat.jar"
else
  echo "Error: keytool not found. Install a JDK with keytool available." >&2
  exit 1
fi

