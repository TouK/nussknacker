#!/usr/bin/env bash

set -e

RUN_IN_BACKGROUND=${RUN_IN_BACKGROUND:-false}

NUSSKNACKER_DIR=`dirname "$0" | xargs -I{} readlink -f {}/..`
CONF_DIR="$NUSSKNACKER_DIR/conf"
LIB_DIR="$NUSSKNACKER_DIR/lib"
export COMPONENTS_DIR="$NUSSKNACKER_DIR/components"

CLASSPATH=${CLASSPATH:-$LIB_DIR/*}
CONFIG_FILE=${CONFIG_FILE:-$CONF_DIR/application.conf}
LOGBACK_FILE=${LOGBACK_FILE:-$CONF_DIR/logback.xml}

WORKING_DIR=${WORKING_DIR:-$NUSSKNACKER_DIR}
export LOGS_DIR=${LOGS_DIR:-$WORKING_DIR/logs}
LOG_FILE="$LOGS_DIR/nussknacker-kafka-runtime.out"
PID_FILE="$WORKING_DIR/nussknacker-kafka-runtime.pid"

export KAFKA_ADDRESS=${KAFKA_ADDRESS:-localhost:9092}
export SCHEMA_REGISTRY_URL=${SCHEMA_REGISTRY_URL:-http://localhost:8082}

mkdir -p $LOGS_DIR
cd $WORKING_DIR

if [[ "${RUN_IN_BACKGROUND}" == "true" ]]; then
  echo -e "JVM: `java --version`\n" >> $LOG_FILE 2>&1
  echo "Starting Nussknacker Kafka Runtime in background"
  export CONSOLE_THRESHOLD_LEVEL=OFF
  set -x
  exec java $JDK_JAVA_OPTIONS -Dconfig.override_with_env_vars=true -Dlogback.configurationFile=$LOGBACK_FILE -Dnussknacker.config.locations=$CONFIG_FILE -cp "$CLASSPATH" pl.touk.nussknacker.engine.lite.kafka.NuKafkaRuntimeApp "$*" >> $LOG_FILE 2>&1 &
  set +x
  echo $! > $PID_FILE
  echo "Nussknacker Kafka Runtime up and running"
else
  echo -e "JVM: `java --version`\n"
  echo "Starting Nussknacker Kafka Runtime"
  set -x
  exec java $JDK_JAVA_OPTIONS -Dconfig.override_with_env_vars=true -Dlogback.configurationFile=$LOGBACK_FILE -Dnussknacker.config.locations=$CONFIG_FILE -cp "$CLASSPATH" pl.touk.nussknacker.engine.lite.kafka.NuKafkaRuntimeApp "$*"
  set +x
fi
