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
export COMPONENTS_DIR="$NUSSKNACKER_DIR/components"

CLASSPATH=${CLASSPATH:-$LIB_DIR/*}
CONFIG_FILE=${CONFIG_FILE-"$CONF_DIR/application.conf"}
SCENARIO_FILE=${SCENARIO_FILE-"$CONF_DIR/scenario.json"}
DEPLOYMENT_CONFIG_FILE=${DEPLOYMENT_CONFIG_FILE-"$CONF_DIR/deploymentConfig.conf"}

# For k8s deployments we crop POD_NAME to last part which is an id of replica (hash) to make metrics tags shorten
if [ -n "$POD_NAME" ]; then
  export INSTANCE_ID=${POD_NAME##*-}
fi

WORKING_DIR=${WORKING_DIR:-$NUSSKNACKER_DIR}

echo "Starting Nussknacker Kafka Runtime"

exec java $JAVA_DEBUG_OPTS -Dlogback.configurationFile="$LOGBACK_FILE" \
          -Dnussknacker.config.locations="$CONFIG_FILE" -Dconfig.override_with_env_vars=true \
          -cp "$CLASSPATH" "pl.touk.nussknacker.engine.lite.kafka.NuKafkaRuntimeApp" "$SCENARIO_FILE" "$DEPLOYMENT_CONFIG_FILE"
