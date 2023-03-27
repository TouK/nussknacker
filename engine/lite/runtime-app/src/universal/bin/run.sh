#!/usr/bin/env bash

set -e

RUN_IN_BACKGROUND=${RUN_IN_BACKGROUND:-false}

NUSSKNACKER_DIR=`dirname "$0" | xargs -I{} readlink -f {}/..`
CONF_DIR="$NUSSKNACKER_DIR/conf"
LIB_DIR="$NUSSKNACKER_DIR/lib"

CLASSPATH=${CLASSPATH:-$LIB_DIR/*}
CONFIG_FILE=${CONFIG_FILE:-$CONF_DIR/application.conf}
LOGBACK_FILE=${LOGBACK_FILE:-$CONF_DIR/logback.xml}

WORKING_DIR=${WORKING_DIR:-$NUSSKNACKER_DIR}
export LOGS_DIR=${LOGS_DIR:-$WORKING_DIR/logs}
LOG_FILE="$LOGS_DIR/nussknacker-runtime.out"
PID_FILE="$WORKING_DIR/nussknacker-runtime.pid"

export KAFKA_ADDRESS=${KAFKA_ADDRESS:-localhost:9092}
export SCHEMA_REGISTRY_URL=${SCHEMA_REGISTRY_URL:-http://localhost:8082}

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

mkdir -p $LOGS_DIR
cd $WORKING_DIR

if [[ "${RUN_IN_BACKGROUND}" == "true" ]]; then
  echo -e "JVM: `java -version`\n" >> $LOG_FILE 2>&1
  echo "Starting Nussknacker Lite Runtime in background"
  export CONSOLE_THRESHOLD_LEVEL=OFF
  set -x
  exec java $JDK_JAVA_OPTIONS $JAVA_PROMETHEUS_OPTS -Dconfig.override_with_env_vars=true -Dlogback.configurationFile=$LOGBACK_FILE -Dnussknacker.config.locations=$CONFIG_FILE -cp "$CLASSPATH" pl.touk.nussknacker.engine.lite.app.NuRuntimeApp "$@" >> $LOG_FILE 2>&1 &
  set +x
  echo $! > $PID_FILE
  echo "Nussknacker Lite Runtime up and running"
else
  echo -e "JVM: `java -version`\n"
  echo "Starting Nussknacker Lite Runtime"
  set -x
  exec java $JDK_JAVA_OPTIONS $JAVA_PROMETHEUS_OPTS -Dconfig.override_with_env_vars=true -Dlogback.configurationFile=$LOGBACK_FILE -Dnussknacker.config.locations=$CONFIG_FILE -cp "$CLASSPATH" pl.touk.nussknacker.engine.lite.app.NuRuntimeApp "$@"
  set +x
fi
