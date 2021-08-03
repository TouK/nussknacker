#!/usr/bin/env bash

set -e

NUSSKNACKER_DIR=`dirname "$0" | xargs -I{} readlink -f {}/..`
CONF_DIR="$NUSSKNACKER_DIR/conf"
LIB_DIR="$NUSSKNACKER_DIR/lib"
MANAGERS_DIR="$NUSSKNACKER_DIR/managers"

CLASSPATH=${CLASSPATH:-$LIB_DIR/*:$MANAGERS_DIR/*}
CONFIG_FILE=${CONFIG_FILE-${2-"$CONF_DIR/application.conf"}}
LOGBACK_FILE=${LOGBACK_FILE-${3-"$CONF_DIR/docker-logback.xml"}}

WORKING_DIR=${WORKING_DIR:-${NUSSKNACKER_DIR}}
export STORAGE_DIR="${STORAGE_DIR:-$NUSSKNACKER_DIR/storage}"

mkdir -p ${STORAGE_DIR}/db

chmod -R ug+wr ${STORAGE_DIR}

echo "Nussknacker up and running with" \
     "CONFIG: $CONFIG_FILE," \
     "LOG: $LOGBACK_FILE"

exec java $JDK_JAVA_OPTIONS -Dlogback.configurationFile="$LOGBACK_FILE" \
          -Dnussknacker.config.locations="$CONFIG_FILE" -Dconfig.override_with_env_vars=true \
          -cp "$CLASSPATH" "pl.touk.nussknacker.ui.NussknackerApp"
