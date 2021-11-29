#!/usr/bin/env bash

set -e

export WORKING_DIR=`dirname "$0" | xargs -I{} readlink -f {}/server/work`
mkdir -p "$WORKING_DIR"
cd $WORKING_DIR

SCALA_VERSION=${SCALA_VERSION:-2.12}
PROJECT_BASE_DIR="../../.."
#management jars are currently needed to access DeploymentManagers
FLINK_MANAGER_JAR=$PROJECT_BASE_DIR/engine/flink/management/target/scala-${SCALA_VERSION}/nussknacker-flink-manager.jar
REQUEST_RESPONSE_MANAGER_JAR=$PROJECT_BASE_DIR/engine/base/request-response/runtime/target/scala-${SCALA_VERSION}/nussknacker-request-response-manager.jar

export CLASSPATH="../target/scala-${SCALA_VERSION}/nussknacker-ui-assembly.jar:$FLINK_MANAGER_JAR:$REQUEST_RESPONSE_MANAGER_JAR"
DIST_BASE_DIR="$PROJECT_BASE_DIR/nussknacker-dist/src/universal"
export CONFIG_FILE="$DIST_BASE_DIR/conf/dev-application.conf"
export NUSSKNACKER_LOG_LEVEL=DEBUG
export CONSOLE_THRESHOLD_LEVEL=DEBUG

export DEV_MODEL_DIR="$PROJECT_BASE_DIR/engine/flink/management/sample/target/scala-${SCALA_VERSION}"
export GENERIC_MODEL_DIR="$PROJECT_BASE_DIR/engine/flink/generic/target/scala-${SCALA_VERSION}"
export LITE_MODEL_DIR="$PROJECT_BASE_DIR/engine/base/model/target/scala-${SCALA_VERSION}"
export REQUEST_RESPONSE_MODEL_DIR="$PROJECT_BASE_DIR/engine/base/request-response/runtime/sample/target/scala-${SCALA_VERSION}"

export FLINK_BASE_COMPONENT_DIR="$PROJECT_BASE_DIR/engine/flink/components/base/target/scala-${SCALA_VERSION}"
export FLINK_KAFKA_COMPONENT_DIR="$PROJECT_BASE_DIR/engine/flink/components/kafka/target/scala-${SCALA_VERSION}"
export LITE_BASE_COMPONENT_DIR="$PROJECT_BASE_DIR/engine/base/components/base/target/scala-${SCALA_VERSION}"
export LITE_KAFKA_COMPONENT_DIR="$PROJECT_BASE_DIR/engine/base/components/kafka/target/scala-${SCALA_VERSION}"

export OPENAPI_COMPONENT_DIR="$PROJECT_BASE_DIR/components/openapi/target/scala-${SCALA_VERSION}"
export SQL_COMPONENT_DIR="$PROJECT_BASE_DIR/components/sql/target/scala-${SCALA_VERSION}"

export OPENAPI_SERVICE_URL="http://localhost:5000"
export SQL_ENRICHER_URL="localhost:5432"

USE_DOCKER_ENV=${USE_DOCKER_ENV:-true}

if [[ "${USE_DOCKER_ENV}" == "true" ]]; then
  echo "Using environment from docker"
  # See https://github.com/TouK/nussknacker-quickstart/blob/main/docker-compose-env.yml - mapped port from docker
  export FLINK_REST_URL="http://localhost:3031"
  export FLINK_QUERYABLE_STATE_PROXY_URL="localhost:3063"
  export FLINK_SHOULD_VERIFY_BEFORE_DEPLOY=${FLINK_SHOULD_VERIFY_BEFORE_DEPLOY:-false}
  # Addresses that should be visible from Flink
  export KAFKA_ADDRESS="localhost:3032"
  export SCHEMA_REGISTRY_URL="http://localhost:3082"
  export GRAFANA_URL="http://localhost:8081/grafana"
  export INFLUXDB_URL="http://localhost:3086/query"
else
  echo "Using local environment"
fi

$DIST_BASE_DIR/bin/run.sh
