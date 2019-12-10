#!/usr/bin/env bash

set -e

export WORKING_DIR=`dirname "$0" | xargs -I{} readlink -f {}/server/work`
mkdir -p "$WORKING_DIR"
cd $WORKING_DIR

SCALA_VERSION=${SCALA_VERSION:-2.12}
export CLASSPATH="../target/scala-${SCALA_VERSION}/nussknacker-ui-assembly.jar"
PROJECT_BASE_DIR="../../.."
DIST_BASE_DIR="$PROJECT_BASE_DIR/nussknacker-dist/src/universal"
export CONFIG_FILE="$DIST_BASE_DIR/conf/dev-application.conf"

export MANAGEMENT_MODEL_DIR="$PROJECT_BASE_DIR/engine/flink/management/sample/target/scala-${SCALA_VERSION}"
export GENERIC_MODEL_DIR="$PROJECT_BASE_DIR/engine/flink/generic/target/scala-${SCALA_VERSION}"
export DEMO_MODEL_DIR="$PROJECT_BASE_DIR/engine/demo/target/scala-${SCALA_VERSION}"
export STANDALONE_MODEL_DIR="$PROJECT_BASE_DIR/engine/standalone/engine/sample/target/scala-${SCALA_VERSION}"

$DIST_BASE_DIR/bin/run.sh