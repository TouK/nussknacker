#!/usr/bin/env bash

NUSSKNACKER_DIR=`dirname "$0" | xargs -I{} readlink -f {}/..`
CONF_DIR="$NUSSKNACKER_DIR/conf"
LIB_DIR="$NUSSKNACKER_DIR/lib"

CONFIG_FILE=${NUSSKNACKER_CONFIG_FILE-"$CONF_DIR/docker-application.conf"}
LOG_FILE=${NUSSKNACKER_LOG_FILE-"$CONF_DIR/docker-logback.xml"}
APPLICATION_PORT=${NUSSKNACKER_APPLICATION_PORT-8080}

exec java \
    -cp "$LIB_DIR/*" \
    -Dlogback.configurationFile="$LOG_FILE" \
    -Dconfig.file="$CONFIG_FILE" \
    pl.touk.nussknacker.ui.NussknackerApp "$APPLICATION_PORT"

echo "Nussknacker up and running with: " \
     "CONFIG: $CONFIG_FILE, " \
     "LOG: $CONFIG_FILE, " \
     "PORT: $APPLICATION_PORT"
