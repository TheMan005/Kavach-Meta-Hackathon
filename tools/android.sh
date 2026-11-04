#!/bin/sh
# Prefer the project-local build tools installed during prototyping; allow normal environment overrides.
set -eu
KAVACH_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
if [ -z "${JAVA_HOME:-}" ] && [ -d "$KAVACH_ROOT/.tooling/jdk" ]; then
    export JAVA_HOME="$KAVACH_ROOT/.tooling/jdk"
fi
if [ -z "${ANDROID_HOME:-}" ] && [ -d "$KAVACH_ROOT/.tooling/sdk" ]; then
    export ANDROID_HOME="$KAVACH_ROOT/.tooling/sdk"
fi
exec "$KAVACH_ROOT/gradlew" -p "$KAVACH_ROOT" "$@"
