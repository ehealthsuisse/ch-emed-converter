#!/usr/bin/env sh
# Starts the CH EMED converter CLI, e.g.:
#   ./ch-emed-converter.sh convert --from chmed16a --to ch-emed --author author.json plan.json
#
# Requires JAVA_HOME to point to a JDK/JRE 21 or newer (Java 21+ is mandatory; Java 11 or 17 do
# not work). If JAVA_HOME is not set in your environment, add a line like the following directly
# below this comment block:
#   JAVA_HOME=/path/to/jdk-21; export JAVA_HOME

if [ -z "$JAVA_HOME" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "JAVA_HOME is not set or does not contain bin/java. Set JAVA_HOME to a Java 21+ installation" >&2
  echo "or add a line 'JAVA_HOME=/path/to/jdk-21; export JAVA_HOME' to this script." >&2
  exit 2
fi

DIR="$(cd "$(dirname "$0")" && pwd)"
exec "$JAVA_HOME/bin/java" -jar "$DIR/ch-emed-converter.jar" "$@"
