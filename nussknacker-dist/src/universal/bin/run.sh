#!/usr/bin/env bash

set -e

RUN_IN_BACKGROUND=${RUN_IN_BACKGROUND:-false}

NUSSKNACKER_DIR=`dirname "$0" | xargs -I{} readlink -f {}/..`
CONF_DIR="$NUSSKNACKER_DIR/conf"
LIB_DIR="$NUSSKNACKER_DIR/lib"
MANAGERS_DIR="$NUSSKNACKER_DIR/managers"

CLASSPATH=${CLASSPATH:-$LIB_DIR/*:$MANAGERS_DIR/*}
CONFIG_FILE=${CONFIG_FILE:-$CONF_DIR/application.conf}
LOGBACK_FILE=${LOGBACK_FILE:-$CONF_DIR/logback.xml}

WORKING_DIR=${WORKING_DIR:-$NUSSKNACKER_DIR}
export LOGS_DIR=${LOGS_DIR:-$WORKING_DIR/logs}
LOG_FILE="$LOGS_DIR/nussknacker-designer.out"
PID_FILE="$WORKING_DIR/nussknacker-designer.pid"

export AUTHENTICATION_USERS_FILE=${AUTHENTICATION_USERS_FILE:-$CONF_DIR/users.conf}
export STORAGE_DIR=${STORAGE_DIR:-$WORKING_DIR/storage}

export FLINK_REST_URL=${FLINK_REST_URL:-http://localhost:8081}
export FLINK_QUERYABLE_STATE_PROXY_URL=${FLINK_QUERYABLE_STATE_PROXY_URL:-localhost:9069}
export KAFKA_ADDRESS=${KAFKA_ADDRESS:-localhost:9092}
# Port is other then default (8081) to avoid collision with Flink REST API
export SCHEMA_REGISTRY_URL=${SCHEMA_REGISTRY_URL:-http://localhost:8082}
export GRAFANA_URL=${GRAFANA_URL:-http://localhost:3000}
export COUNTS_URL=${COUNTS_URL:-http://localhost:8086/query}

mkdir -p $LOGS_DIR
cd $WORKING_DIR


if [[ "${RUN_IN_BACKGROUND}" == "true" ]]; then
  echo "Starting Nussknacker in background"
  export CONSOLE_THRESHOLD_LEVEL=OFF
  set -x
  exec java $JDK_JAVA_OPTIONS -Dconfig.override_with_env_vars=true -Dlogback.configurationFile=$LOGBACK_FILE -Dnussknacker.config.locations=$CONFIG_FILE -cp "$CLASSPATH" pl.touk.nussknacker.ui.NussknackerApp >> $LOG_FILE 2>&1 &
  set +x
  echo $! > $PID_FILE
  echo "Nussknacker up and running"
else
  echo "Starting Nussknacker"
  set -x
  exec java $JDK_JAVA_OPTIONS -Dconfig.override_with_env_vars=true -Dlogback.configurationFile=$LOGBACK_FILE -Dnussknacker.config.locations=$CONFIG_FILE -cp "$CLASSPATH" pl.touk.nussknacker.ui.NussknackerApp
  set +x
fi
