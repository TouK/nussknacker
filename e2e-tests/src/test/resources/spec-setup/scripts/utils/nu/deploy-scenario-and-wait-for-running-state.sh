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
  if [ "$#" -ne 1 ]; then
      echo "Error: One parameter required: 1) scenario name"
      exit 11
  fi

  set -e

  local SCENARIO_NAME=$1

  local RESPONSE
  RESPONSE=$(curl -s -L -w "\n%{http_code}" -u admin:admin \
    -X POST "http://nginx:8080/api/processManagement/deploy/$SCENARIO_NAME"
  )

  local HTTP_STATUS
  HTTP_STATUS=$(echo "$RESPONSE" | tail -n 1)

  if [ "$HTTP_STATUS" != "200" ]; then
    local RESPONSE_BODY
    RESPONSE_BODY=$(echo "$RESPONSE" | sed \$d)
    echo -e "Error: Cannot run scenario $SCENARIO_NAME deployment.\nHTTP status: $HTTP_STATUS, response body: $RESPONSE_BODY"
    exit 12
  fi

  echo "Scenario $SCENARIO_NAME deployment started ..."
}

function checkDeploymentStatus() {
  if [ "$#" -ne 1 ]; then
    echo "Error: One parameter required: 1) scenario name"
    exit 21
  fi

  set -e

  local SCENARIO_NAME=$1

  local RESPONSE
  RESPONSE=$(curl -s -L -w "\n%{http_code}" -u admin:admin \
    -X GET "http://nginx:8080/api/processes/$SCENARIO_NAME/status"
  )

  local HTTP_STATUS
  HTTP_STATUS=$(echo "$RESPONSE" | tail -n 1)
  local RESPONSE_BODY
  RESPONSE_BODY=$(echo "$RESPONSE" | sed \$d)

  if [ "$HTTP_STATUS" != "200" ]; then
    echo -e "Error: Cannot check scenario $SCENARIO_NAME deployment status.\nHTTP status: $HTTP_STATUS, response body: $RESPONSE_BODY"
    exit 22
  fi

  local SCENARIO_STATUS
  SCENARIO_STATUS=$(echo "$RESPONSE_BODY" | jq -r '.status.name')
  echo "$SCENARIO_STATUS"
}

echo "Deploying scenario $SCENARIO_NAME ..."

START_TIME=$(date +%s)
END_TIME=$((START_TIME + TIMEOUT_SECONDS))

deployScenario "$SCENARIO_NAME"

while true; do
  DEPLOYMENT_STATUS=$(checkDeploymentStatus "$SCENARIO_NAME")

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
