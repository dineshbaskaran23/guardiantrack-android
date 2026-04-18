#!/usr/bin/env sh
##############################################################################
# Gradle startup script for UNIX
##############################################################################
APP_HOME="`pwd -P`"
APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
JAVA_EXE=java
if [ -n "$JAVA_HOME" ]; then JAVA_EXE="$JAVA_HOME/bin/java"; fi
exec "$JAVA_EXE" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \
  "-Dorg.gradle.appname=$APP_BASE_NAME" \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain "$@"
