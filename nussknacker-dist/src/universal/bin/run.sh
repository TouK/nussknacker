#!/usr/bin/env bash

set -e

RUN_IN_BACKGROUND=${RUN_IN_BACKGROUND:-false}

PARENT_DIR=`dirname "$0" | xargs -I{} readlink -f {}/..`
CONF_DIR="$PARENT_DIR/conf"
LIB_DIR="$PARENT_DIR/lib"
CLASSPATH=${CLASSPATH:-$LIB_DIR/*}
CONFIG_FILE=${CONFIG_FILE:-$CONF_DIR/application.conf}
LOGBACK_FILE=${LOGBACK_FILE:-$CONF_DIR/logback.xml}

WORKING_DIR=${WORKING_DIR:-${PARENT_DIR}}
LOGS_DIR="$WORKING_DIR/logs"
LOG_FILE="$LOGS_DIR/frontend.log"
PID_FILE="$WORKING_DIR/frontend.pid"

export STORAGE_DIR=${STORAGE_DIR:-$WORKING_DIR/storage}

if [[ "${USE_DOCKER_ENV}" == "true" ]]; then
  echo "Using envirnoment from docker"
  export DEVELOPMENT_MODE="true"
  # See demo/docker/docker-compose-env.yml - mapped port from docker
  export GRAFANA_URL="http://localhost:8081/grafana"
  export KIBANA_URL="http://localhost:8081/kibana/"
  export FLINK_REST_URL="http://localhost:3031"
  export FLINK_QUERYABLE_STATE_PROXY_URL="localhost:3063"
elif [[ "${PROXY_URL}" != "" ]]; then
  export PROXY_URL
else
  # PROXY_URL is not set up so we assume that CORS should be enabled
  export DEVELOPMENT_MODE="true"
  export GRAFANA_URL=${GRAFANA_URL:-http://localhost:3000}
  export KIBANA_URL=${KIBANA_URL:-http://localhost:5601}
  export FLINK_REST_URL=${FLINK_REST_URL:-http://localhost:8081}
  export FLINK_QUERYABLE_STATE_PROXY_URL=${FLINK_QUERYABLE_STATE_PROXY_URL:-localhost:9069}
  export KAFKA_ADDRESS=${KAFKA_ADDRESS:-localhost:9092}
fi

export AUTHENTICATION_USERS_FILE=${AUTHENTICATION_USERS_FILE:-$CONF_DIR/users.conf}

mkdir -p $LOGS_DIR

cd $WORKING_DIR
if [[ "${RUN_IN_BACKGROUND}" == "true" ]]; then
  echo "Runnig: java -Dlogback.configurationFile=$LOGBACK_FILE -Dconfig.file=$CONFIG_FILE -cp \"$CLASSPATH\" pl.touk.nussknacker.ui.NussknackerApp >> $LOG_FILE 2>&1"
  exec java -Dlogback.configurationFile=$LOGBACK_FILE -Dconfig.file=$CONFIG_FILE -cp "$CLASSPATH" pl.touk.nussknacker.ui.NussknackerApp >> $LOG_FILE 2>&1 &
  echo $! > $PID_FILE
  echo "Nussknacker up and running"
else
  echo "Runnig: java -Dlogback.configurationFile=$LOGBACK_FILE -Dconfig.file=$CONFIG_FILE -cp \"$CLASSPATH\" pl.touk.nussknacker.ui.NussknackerApp"
  exec java -Dlogback.configurationFile=$LOGBACK_FILE -Dconfig.file=$CONFIG_FILE -cp "$CLASSPATH" pl.touk.nussknacker.ui.NussknackerApp
fi