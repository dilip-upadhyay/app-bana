#!/usr/bin/env sh
set -e
BASEDIR=$(cd "$(dirname "$0")" && pwd)
# Prefer system mvn if present
if command -v mvn >/dev/null 2>&1; then
  exec mvn "$@"
fi
# Use local .mvn/apache-maven if already downloaded
MAVEN_DIR="$BASEDIR/.mvn/apache-maven"
MAVEN_BIN="$MAVEN_DIR/bin/mvn"
if [ -x "$MAVEN_BIN" ]; then
  exec "$MAVEN_BIN" "$@"
fi
# Download and extract Apache Maven
MAVEN_VERSION="3.9.8"
ARCHIVE_NAME="apache-maven-${MAVEN_VERSION}-bin.tar.gz"
URL_PRIMARY="https://dlcdn.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/${ARCHIVE_NAME}"
URL_FALLBACK="https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/${ARCHIVE_NAME}"
TMPDIR="$BASEDIR/.mvn/tmp"
mkdir -p "$TMPDIR"
ARCHIVE_PATH="$TMPDIR/$ARCHIVE_NAME"
if [ ! -f "$ARCHIVE_PATH" ]; then
  echo "Downloading Maven ${MAVEN_VERSION}..."
  if command -v curl >/dev/null 2>&1; then
    if curl -fsSL -o "$ARCHIVE_PATH" "$URL_PRIMARY"; then
      true
    else
      echo "Primary URL failed, trying archive mirror..."
      curl -fsSL -o "$ARCHIVE_PATH" "$URL_FALLBACK"
    fi
  elif command -v wget >/dev/null 2>&1; then
    if wget -q -O "$ARCHIVE_PATH" "$URL_PRIMARY"; then
      true
    else
      echo "Primary URL failed, trying archive mirror..."
      wget -q -O "$ARCHIVE_PATH" "$URL_FALLBACK"
    fi
  else
    echo "curl or wget required to download Maven" >&2
    exit 1
  fi
fi
mkdir -p "$MAVEN_DIR"
# extract
tar -xzf "$ARCHIVE_PATH" -C "$TMPDIR"
EXTRACTED="$TMPDIR/apache-maven-${MAVEN_VERSION}"
rm -rf "$MAVEN_DIR"
mv "$EXTRACTED" "$MAVEN_DIR"
# run
exec "$MAVEN_BIN" "$@"
