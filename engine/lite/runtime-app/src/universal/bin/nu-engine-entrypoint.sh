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
LOGBACK_FILE=${LOGBACK_FILE:-$CONF_DIR/logback.xml}

CLASSPATH=${CLASSPATH:-$LIB_DIR/*}
CONFIG_FILE=${CONFIG_FILE-"$CONF_DIR/application.conf"}
SCENARIO_FILE=${SCENARIO_FILE-"$CONF_DIR/scenario.json"}
DEPLOYMENT_CONFIG_FILE=${DEPLOYMENT_CONFIG_FILE-"$CONF_DIR/deploymentConfig.conf"}

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

# For k8s deployments we crop POD_NAME to last part which is an id of replica (hash) to make metrics tags shorten
if [ -n "$POD_NAME" ]; then
  export INSTANCE_ID=${POD_NAME##*-}
fi

WORKING_DIR=${WORKING_DIR:-$NUSSKNACKER_DIR}

echo "Starting Nussknacker Lite Runtime"

exec java $JAVA_DEBUG_OPTS $JAVA_PROMETHEUS_OPTS -Dlogback.configurationFile="$LOGBACK_FILE" \
          -Dnussknacker.config.locations="$CONFIG_FILE" -Dconfig.override_with_env_vars=true \
          -cp "$CLASSPATH" "pl.touk.nussknacker.engine.lite.app.NuRuntimeApp" "$SCENARIO_FILE" "$DEPLOYMENT_CONFIG_FILE"
