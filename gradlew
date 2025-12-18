#!/bin/bash

##############################################################################
# Gradle wrapper script for Unix
##############################################################################

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD="maximum"

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

# Add default JVM options here.
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Download gradle wrapper if not present
WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$WRAPPER_JAR" ]; then
    echo "Downloading Gradle Wrapper..."
    mkdir -p gradle/wrapper
    curl -L -o "$WRAPPER_JAR" "https://github.com/gradle/gradle/raw/v8.2.0/gradle/wrapper/gradle-wrapper.jar"
fi

exec "$JAVACMD" $DEFAULT_JVM_OPTS -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
