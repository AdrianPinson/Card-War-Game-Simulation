#!/bin/sh
set -eu
war_home=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -n "${JAVA_HOME:-}" ]; then
  war_java="$JAVA_HOME/bin/java"
else
  war_java=java
fi
exec "$war_java" -cp "$war_home/war-simulation.jar:$war_home/lib/*" war.ui.DesktopLauncher "$@"
