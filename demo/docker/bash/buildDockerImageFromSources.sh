#!/usr/bin/env bash
set -e
source $(dirname "$0")/lib.sh
DOCKER_APP_LOCATION="${DOCKER_DEMO_PATH}/app/build/"


echo "Building Nussknacker UI"
buildModule ui
UI_JAR=$(findJar ui)
cp ${UI_JAR} ${DOCKER_APP_LOCATION}

docker-compose build --no-cache app
rm "${DOCKER_APP_LOCATION}/nussknacker-ui-assembly.jar"