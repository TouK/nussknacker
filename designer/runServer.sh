#!/usr/bin/env bash

set -e

export WORKING_DIR=`dirname "$0" | xargs -I{} readlink -f {}/server/work`
export NUSSKNACKER_DIR=$WORKING_DIR
mkdir -p "$WORKING_DIR"
cd $WORKING_DIR

NUSSKNACKER_SCALA_VERSION=${NUSSKNACKER_SCALA_VERSION:-2.13}
PROJECT_BASE_DIR="../../.."

DIST_BASE_DIR="$PROJECT_BASE_DIR/nussknacker-dist/src/universal"
export LIB_DIR="${PROJECT_BASE_DIR}/designer/server/target/scala-$NUSSKNACKER_SCALA_VERSION"
export CONF_DIR="${DIST_BASE_DIR}/conf"
export CONFIG_FILE="$DIST_BASE_DIR/conf/dev-application.conf"
export NUSSKNACKER_LOG_LEVEL=DEBUG
export CONSOLE_THRESHOLD_LEVEL=DEBUG

export OPENAPI_SERVICE_URL="http://localhost:5000"
export SQL_ENRICHER_URL="localhost:5432"
export USAGE_REPORTS_FINGERPRINT="development"
export USAGE_REPORTS_SOURCE="sources"

USE_DOCKER_ENV=${USE_DOCKER_ENV:-true}

if [[ "${USE_DOCKER_ENV}" == "true" ]]; then
  echo "Using environment from docker"
  # See https://github.com/TouK/nussknacker-quickstart/blob/main/docker-compose-env.yml - mapped port from docker
  export FLINK_REST_URL="http://localhost:3031"
  export FLINK_SHOULD_VERIFY_BEFORE_DEPLOY=${FLINK_SHOULD_VERIFY_BEFORE_DEPLOY:-false}
  # Addresses that should be visible from Flink
  export KAFKA_ADDRESS="localhost:3032"
  export SCHEMA_REGISTRY_URL="http://localhost:3082"
  export GRAFANA_URL="http://localhost:8081/grafana"
  export INFLUXDB_URL="http://localhost:3086/query"
  export INPUT_CONFIG_RESOLVE_ENV_VARIABLES="false"
else
  echo "Using local environment"
fi

export JAVA_DEBUG_PORT=5005

$DIST_BASE_DIR/bin/run.sh
