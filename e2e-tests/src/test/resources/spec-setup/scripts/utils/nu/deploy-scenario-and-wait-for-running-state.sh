#!/bin/bash -e

cd "$(dirname "$0")"

if [ "$#" -lt 1 ]; then
  echo "Error: One parameter required: 1) scenario name"
  exit 1
fi

SCENARIO_NAME=$1
TIMEOUT_SECONDS=${2:-60}
WAIT_INTERVAL=5

function deployScenario() {
  if [ "$#" -ne 2 ]; then
      echo "Error: Two parameters required: 1) scenario name, 2) deployment UUID"
      exit 11
  fi

  set -e

  local SCENARIO_NAME=$1
  local DEPLOYMENT_ID=$2

  local REQUEST_BODY="{
    \"scenarioName\": \"$SCENARIO_NAME\",
    \"nodesDeploymentData\": {},
    \"comment\": \"\"
  }"

  local RESPONSE
  RESPONSE=$(curl -s -L -w "\n%{http_code}" -u admin:admin \
    -X PUT "http://nginx:8080/api/deployments/$DEPLOYMENT_ID" \
    -H "Content-Type: application/json" -d "$REQUEST_BODY"
  )

  local HTTP_STATUS
  HTTP_STATUS=$(echo "$RESPONSE" | tail -n 1)

  if [ "$HTTP_STATUS" != "202" ]; then
    local RESPONSE_BODY
    RESPONSE_BODY=$(echo "$RESPONSE" | sed \$d)
    echo -e "Error: Cannot run scenario $SCENARIO_NAME deployment.\nHTTP status: $HTTP_STATUS, response body: $RESPONSE_BODY"
    exit 12
  fi

  echo "Scenario $SCENARIO_NAME deployment started ..."
}

function checkDeploymentStatus() {
  if [ "$#" -ne 1 ]; then
    echo "Error: One parameter required: 1) deployment UUID"
    exit 21
  fi

  set -e

  local DEPLOYMENT_ID=$1

  local RESPONSE
  RESPONSE=$(curl -s -L -w "\n%{http_code}" -u admin:admin \
    -X GET "http://nginx:8080/api/deployments/$DEPLOYMENT_ID/status"
  )

  local HTTP_STATUS
  HTTP_STATUS=$(echo "$RESPONSE" | tail -n 1)
  local RESPONSE_BODY
  RESPONSE_BODY=$(echo "$RESPONSE" | sed \$d)

  if [ "$HTTP_STATUS" != "200" ]; then
    echo -e "Error: Cannot check deployment $DEPLOYMENT_ID status.\nHTTP status: $HTTP_STATUS, response body: $RESPONSE_BODY"
    exit 22
  fi

  local SCENARIO_STATUS
  SCENARIO_STATUS=$(echo "$RESPONSE_BODY")
  echo "$SCENARIO_STATUS"
}

echo "Deploying scenario $SCENARIO_NAME ..."

START_TIME=$(date +%s)
END_TIME=$((START_TIME + TIMEOUT_SECONDS))

DEPLOYMENT_ID=$(uuidgen)
deployScenario "$SCENARIO_NAME" "$DEPLOYMENT_ID"

while true; do
  DEPLOYMENT_STATUS=$(checkDeploymentStatus "$DEPLOYMENT_ID")

  if [ "$DEPLOYMENT_STATUS" == "RUNNING" ]; then
    break
  fi

  CURRENT_TIME=$(date +%s)
  if [ $CURRENT_TIME -gt $END_TIME ]; then
    echo "Error: Timeout for waiting for the RUNNING state of $SCENARIO_NAME deployment reached!"
    exit 2
  fi

  echo "$SCENARIO_NAME deployment state is $DEPLOYMENT_STATUS. Checking again in $WAIT_INTERVAL seconds..."
  sleep $WAIT_INTERVAL
done

echo "Scenario $SCENARIO_NAME is RUNNING!"
