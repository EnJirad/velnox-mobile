#!/bin/sh
# Velnox Gradle wrapper.
#
# No gradle-wrapper.jar is committed. The wrapper jar embeds
# libnative-platform.so for x86_64 only, which breaks aarch64 checkouts and
# forces a binary into the repo. CI installs Gradle 8.11.1 (see
# .github/workflows/gradle-build.yml) and this script simply delegates to it.
#
# Set GRADLE_HOME or put `gradle` on PATH before running ./gradlew.

set -e

if [ -n "$GRADLE_HOME" ] && [ -x "$GRADLE_HOME/bin/gradle" ]; then
  exec "$GRADLE_HOME/bin/gradle" "$@"
fi

if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi

echo "ERROR: gradle not found." >&2
echo "Install Gradle 8.11.1 or set GRADLE_HOME=/path/to/gradle-8.11.1" >&2
exit 1
