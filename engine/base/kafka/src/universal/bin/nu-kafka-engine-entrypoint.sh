#!/usr/bin/env bash

set -e

NUSSKNACKER_DIR=`dirname "$0" | xargs -I{} readlink -f {}/..`
CONF_DIR="$NUSSKNACKER_DIR/conf"
LIB_DIR="$NUSSKNACKER_DIR/lib"
export COMPONENTS_DIR="$NUSSKNACKER_DIR/components"

CLASSPATH=${CLASSPATH:-$LIB_DIR/*}
CONFIG_FILE=${CONFIG_FILE-"$CONF_DIR/application.conf"}
SCENARIO_FILE=${SCENARIO_FILE-"$CONF_DIR/scenario.json"}
LOGBACK_FILE=${LOGBACK_FILE-"$CONF_DIR/logback.xml"}

WORKING_DIR=${WORKING_DIR:-$NUSSKNACKER_DIR}


echo "Starting Nussknacker Kafka Engine "

exec java $JDK_JAVA_OPTIONS -Dlogback.configurationFile="$LOGBACK_FILE" \
          -Dnussknacker.config.locations="$CONFIG_FILE" -Dconfig.override_with_env_vars=true \
          -cp "$CLASSPATH" "pl.touk.nussknacker.engine.baseengine.kafka.NuKafkaEngineApp" "$SCENARIO_FILE"
