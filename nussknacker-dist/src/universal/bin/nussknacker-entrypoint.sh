#!/usr/bin/env bash

set -e

if [ "$JAVA_DEBUG_PORT" == "" ]; then
  JAVA_DEBUG_OPTS=""
else
  JAVA_DEBUG_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:$JAVA_DEBUG_PORT"
fi

NUSSKNACKER_DIR=`dirname "$0" | xargs -I{} readlink -f {}/..`
CONF_DIR="$NUSSKNACKER_DIR/conf"
LIB_DIR="$NUSSKNACKER_DIR/lib"

CLASSPATH=${CLASSPATH:-$LIB_DIR/*}
CONFIG_FILE=${CONFIG_FILE-"$CONF_DIR/application.conf"}
LOGBACK_FILE=${LOGBACK_FILE-"$CONF_DIR/docker-logback.xml"}

WORKING_DIR=${WORKING_DIR:-$NUSSKNACKER_DIR}

export AUTHENTICATION_USERS_FILE=${AUTHENTICATION_USERS_FILE:-$CONF_DIR/users.conf}
export TABLES_DEFINITION_FILE=${TABLES_DEFINITION_FILE:-$CONF_DIR/dev-tables-definition.sql}
export STORAGE_DIR="${STORAGE_DIR:-$WORKING_DIR/storage}"

if [ "$PROMETHEUS_METRICS_PORT" == "" ]; then
  JAVA_PROMETHEUS_OPTS=""
else
  agentPath=("$NUSSKNACKER_DIR/jmx_prometheus_javaagent/jmx_prometheus_javaagent-"*.jar)
  if [ "${#agentPath[@]}" != 1 ]; then
      echo "Found no or multiple versions of lib jmx prometheus agent"
      exit 1
  fi
  PROMETHEUS_AGENT_CONFIG_FILE=${PROMETHEUS_AGENT_CONFIG_FILE:-$CONF_DIR/jmx_prometheus.yaml}
  JAVA_PROMETHEUS_OPTS="-javaagent:$agentPath=$PROMETHEUS_METRICS_PORT:$PROMETHEUS_AGENT_CONFIG_FILE"
fi

if [ "$USAGE_REPORTS_SOURCE" == "" ]; then
  export USAGE_REPORTS_SOURCE="docker"
fi

mkdir -p ${STORAGE_DIR}/db

echo "Starting Nussknacker:"

exec java $JDK_JAVA_OPTIONS $JAVA_DEBUG_OPTS $JAVA_PROMETHEUS_OPTS \
          -Dlogback.configurationFile="$LOGBACK_FILE" \
          -Dnussknacker.config.locations="$CONFIG_FILE" -Dconfig.override_with_env_vars=true \
          -cp "$CLASSPATH" "pl.touk.nussknacker.ui.NussknackerApp"
