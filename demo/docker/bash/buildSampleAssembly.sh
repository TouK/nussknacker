#!/usr/bin/env bash
set -e
source $(dirname "$0")/../.env
source $(dirname "$0")/lib.sh
assertDirectory ${CODE_LOCATION}

echo "Building current sample app"
buildModule example
SAMPLE_JAR=$(findJar example)
cp ${SAMPLE_JAR} ${CODE_LOCATION}

