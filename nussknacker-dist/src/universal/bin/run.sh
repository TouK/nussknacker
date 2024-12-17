#!/usr/bin/env bash

set -ex

RUN_IN_BACKGROUND=${RUN_IN_BACKGROUND:-false}

DEFAULT_NUSSKNACKER_DIR=`dirname "$0" | xargs -I{} readlink -f {}/..`
NUSSKNACKER_DIR=${NUSSKNACKER_DIR:-$DEFAULT_NUSSKNACKER_DIR}
CONF_DIR=${CONF_DIR:-"$NUSSKNACKER_DIR/conf"}
LIB_DIR=${LIB_DIR:-"$NUSSKNACKER_DIR/lib"}

CLASSPATH=${CLASSPATH:-$LIB_DIR/*}
CONFIG_FILE=${CONFIG_FILE:-$CONF_DIR/application.conf}
LOGBACK_FILE=${LOGBACK_FILE:-$CONF_DIR/logback.xml}

WORKING_DIR=${WORKING_DIR:-$NUSSKNACKER_DIR}
export LOGS_DIR=${LOGS_DIR:-$WORKING_DIR/logs}
LOG_FILE="$LOGS_DIR/nussknacker-designer.out"
PID_FILE="$WORKING_DIR/nussknacker-designer.pid"

export AUTHENTICATION_USERS_FILE=${AUTHENTICATION_USERS_FILE:-$CONF_DIR/users.conf}
export TABLES_DEFINITION_FILE=${TABLES_DEFINITION_FILE:-$CONF_DIR/dev-tables-definition.sql}
export STORAGE_DIR=${STORAGE_DIR:-$WORKING_DIR/storage}
export MANAGERS_DIR=${MANAGERS_DIR:-$WORKING_DIR/managers}

export FLINK_REST_URL=${FLINK_REST_URL:-http://localhost:8081}
export KAFKA_ADDRESS=${KAFKA_ADDRESS:-localhost:9092}
# Port is other then default (8081) to avoid collision with Flink REST API
export SCHEMA_REGISTRY_URL=${SCHEMA_REGISTRY_URL:-http://localhost:8082}
export GRAFANA_URL=${GRAFANA_URL:-http://localhost:3000}
export INFLUXDB_URL=${INFLUXDB_URL:-http://localhost:8086}

if [ "$JAVA_DEBUG_PORT" == "" ]; then
  JAVA_DEBUG_OPTS=""
else
  JAVA_DEBUG_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:$JAVA_DEBUG_PORT"
fi

if [ "$PROMETHEUS_METRICS_PORT" == "" ]; then
  JAVA_PROMETHEUS_OPTS=""
else
  agentPath=("$NUSSKNACKER_DIR/jmx_prometheus_javaagent/jmx_prometheus_javaagent-"*.jar)
  if [ "${#agentPath[@]}" != 1 ]; then
      echo "Found no or multiple versions of lib jmx prometheus agent"
      exit 1
  fi
  export PROMETHEUS_AGENT_CONFIG_FILE=${PROMETHEUS_AGENT_CONFIG_FILE:-$CONF_DIR/jmx_prometheus.yaml}
  JAVA_PROMETHEUS_OPTS="-javaagent:$agentPath=$PROMETHEUS_METRICS_PORT:$PROMETHEUS_AGENT_CONFIG_FILE"
fi

export USAGE_REPORTS_SOURCE="binaries"

mkdir -p $LOGS_DIR
cd $WORKING_DIR

if [[ "${RUN_IN_BACKGROUND}" == "true" ]]; then
  echo -e "JVM: `java -version`\n" >> $LOG_FILE 2>&1
  echo "Starting Nussknacker in background"
  export CONSOLE_THRESHOLD_LEVEL=OFF
  set -x
  exec java $JDK_JAVA_OPTIONS $JAVA_DEBUG_OPTS $JAVA_PROMETHEUS_OPTS -Dconfig.override_with_env_vars=true -Dlogback.configurationFile=$LOGBACK_FILE -Dnussknacker.config.locations=$CONFIG_FILE -cp "$CLASSPATH" pl.touk.nussknacker.ui.NussknackerApp >> $LOG_FILE 2>&1 &
  set +x
  echo $! > $PID_FILE
  echo "Nussknacker up and running"
else
  echo -e "JVM: `java -version`\n"
  echo "Starting Nussknacker"
  set -x
  exec java $JDK_JAVA_OPTIONS $JAVA_DEBUG_OPTS $JAVA_PROMETHEUS_OPTS -Dconfig.override_with_env_vars=true -Dlogback.configurationFile=$LOGBACK_FILE -Dnussknacker.config.locations=$CONFIG_FILE -cp "$CLASSPATH" pl.touk.nussknacker.ui.NussknackerApp
  set +x
fi
