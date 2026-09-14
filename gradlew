#!/bin/sh
# Standard Gradle wrapper launcher script.
# NOTE: gradle-wrapper.jar is not bundled in this generated project (binary file).
# Run `gradle wrapper --gradle-version 8.7` once locally (with a system Gradle install)
# to generate gradle/wrapper/gradle-wrapper.jar before using this script, or simply
# open the project in Android Studio, which will offer to do this automatically.

DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
APP_HOME=$(cd "$(dirname "$0")" && pwd)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$CLASSPATH" ]; then
  echo "gradle-wrapper.jar not found. Run 'gradle wrapper --gradle-version 8.7' first," >&2
  echo "or open this project in Android Studio to have it generated automatically." >&2
  exit 1
fi

exec java $DEFAULT_JVM_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
