#!/bin/bash -e

cd "$(dirname "$0")"

function importScenario() {
  if [ "$#" -ne 4 ]; then
    echo "Error: Four parameters required: 1) scenario name, 2) example scenario file path, 3) processing mode, 4) engine"
    exit 11
  fi

  set -e

  local EXAMPLE_SCENARIO_NAME=$1
  local EXAMPLE_SCENARIO_FILE=$2
  local EXAMPLE_SCENARIO_PROCESSING_MODE=$3
  local EXAMPLE_SCENARIO_ENGINE=$4

  ../utils/load-scenario-from-json-file.sh "$EXAMPLE_SCENARIO_NAME" "$EXAMPLE_SCENARIO_FILE" "$EXAMPLE_SCENARIO_PROCESSING_MODE" "$EXAMPLE_SCENARIO_ENGINE"
}

echo "Starting to import and deploy example scenarios ..."

while IFS= read -r EXAMPLE_SCENARIO_FILENAME; do

  if [[ $EXAMPLE_SCENARIO_FILENAME == "#"* ]]; then
    continue
  fi

  EXAMPLE_SCENARIO_NAME=$(basename "$EXAMPLE_SCENARIO_FILENAME" ".json")

  importScenario "$EXAMPLE_SCENARIO_NAME" "$(realpath ../../data/scenarios/"$EXAMPLE_SCENARIO_FILENAME")" "Bounded-Stream" "Flink"

done < "../../data/examples.txt"

echo "DONE!"
