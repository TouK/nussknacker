#!/usr/bin/env bash

NUSSKNACKER_DIR=`dirname "$0" | xargs -I{} readlink -f {}/..`
CONF_DIR="$NUSSKNACKER_DIR/conf"
LIB_DIR="$NUSSKNACKER_DIR/lib"

APPLICATION_PORT=${NUSSKNACKER_APPLICATION_PORT-${1-"8080"}}
CONFIG_FILE=${NUSSKNACKER_CONFIG_FILE-${2-"$CONF_DIR/docker-application.conf"}}
LOG_FILE=${NUSSKNACKER_LOG_FILE-${3-"$CONF_DIR/docker-logback.xml"}}
APPLICATION_APP=${NUSSKNACKER_APPLICATION_APP-${4-"pl.touk.nussknacker.ui.NussknackerApp"}}

echo "Nussknacker up and running with" \
     "APP: $APPLICATION_APP" \
     "CONFIG: $CONFIG_FILE," \
     "LOG: $LOG_FILE," \
     "PORT: $APPLICATION_PORT" \
     "LIB_DIR: $LIB_DIR"

exec java -Dlogback.configurationFile="$LOG_FILE" \
          -Dconfig.file="$CONFIG_FILE" \
          -cp "$LIB_DIR/*" \
          "$APPLICATION_APP" "$APPLICATION_PORT"
`