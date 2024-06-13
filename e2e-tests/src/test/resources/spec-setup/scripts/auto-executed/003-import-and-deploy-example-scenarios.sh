#!/bin/bash -ex

cd "$(dirname "$0")"

function importAndDeployScenario() {
  if [ "$#" -ne 2 ]; then
    echo "Error: Two parameters required: 1) scenario name, 2) example scenario file path"
    exit 11
  fi

  set -e
  local EXAMPLE_SCENARIO_NAME=$1
  local EXAMPLE_SCENARIO_FILE=$2

  ../utils/nu/load-scenario-from-json.sh "$EXAMPLE_SCENARIO_NAME" "$EXAMPLE_SCENARIO_FILE"
  ../utils/nu/deploy-scenario-and-wait-for-running-state.sh "$EXAMPLE_SCENARIO_NAME"
}

echo "Starting to import and deploy example scenarios ..."

while IFS= read -r EXAMPLE_SCENARIO_NAME; do

  if [[ $EXAMPLE_SCENARIO_NAME == "#"* ]]; then
    continue
  fi

  importAndDeployScenario "$EXAMPLE_SCENARIO_NAME" "$(realpath ../../data/nu/scenarios/"$EXAMPLE_SCENARIO_NAME.json")"

done < "../../data/nu/examples"

echo "DONE!"
