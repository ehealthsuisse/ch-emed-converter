#!/usr/bin/env sh
# Starts the CH EMED converter REST service, e.g.:
#   ./ch-emed-converter-rest.sh --server.port=8080 --converter.rest.timeout=30s
#
# The service has no authentication and no TLS: run it behind an API gateway or in a protected
# network only (rest-service.md, "Security model").
#
# Requires JAVA_HOME to point to a JDK/JRE 21 or newer (Java 21+ is mandatory; Java 11 or 17 do
# not work). If JAVA_HOME is not set in your environment, add a line like the following directly
# below this comment block:
#   JAVA_HOME=/path/to/jdk-21; export JAVA_HOME
# JVM options (e.g. heap size) can be passed in JAVA_OPTS, e.g. JAVA_OPTS="-Xmx2g".

if [ -z "$JAVA_HOME" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "JAVA_HOME is not set or does not contain bin/java. Set JAVA_HOME to a Java 21+ installation" >&2
  echo "or add a line 'JAVA_HOME=/path/to/jdk-21; export JAVA_HOME' to this script." >&2
  exit 2
fi

DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck disable=SC2086 # JAVA_OPTS may hold several options
exec "$JAVA_HOME/bin/java" $JAVA_OPTS -jar "$DIR/ch-emed-converter-rest.jar" "$@"
