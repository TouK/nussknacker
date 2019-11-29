#!/usr/bin/env bash

set -e

echo "Starting containers"

#just in case
docker-compose -f docker-compose.yml -f docker-compose-env.yml kill
docker-compose -f docker-compose.yml -f docker-compose-env.yml rm -f -v
docker-compose -f docker-compose.yml -f docker-compose-env.yml build
docker-compose -f docker-compose.yml -f docker-compose-env.yml up -d --no-recreate

trap 'docker-compose -f docker-compose.yml -f docker-compose-env.yml kill && docker-compose -f docker-compose.yml -f docker-compose-env.yml rm -f -v' EXIT

#TODO: Consider rewriting below, e.g. in Python
waitTime=0
sleep=10

checkCode() {
  CODE=$(curl -s -o /dev/null -w "%{http_code}" "http://admin:admin@localhost:8081/$1")
  echo "Checked $1 with $CODE"
  if [[ $CODE == 200 ]]
  then
    return 0
  else
    return 1
  fi
}

waitForOK() {
  URL_PATH=$1
  checkCode $URL_PATH
  local OK=$?
  while [[ $waitTime < 80 && $OK == 1 ]]
  do
    echo "Still not started..."
    sleep $sleep
    waitTime=$(($waitTime+$sleep))
    checkCode $URL_PATH
    OK=$?
  done
  if [[ $OK == 1 ]]
  then
    echo "$2"
    exit 1
  fi
}

echo "Waiting for frontend to start"

waitForOK "api/processes" "Frontend not started"

echo "Creating process"
CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "http://admin:admin@localhost:8081/api/processes/DetectLargeTransactions/FraudDetection?isSubprocess=false")
if [[ $CODE == 201 ]]; then
  echo "Process creation success"
elif [[ $CODE == 400 ]]; then
  echo "Process has already exists in db."
else
  echo "Process creation failed with $CODE"
  exit 1
fi

waitForOK "api/processes/status" "Cannot connect with Flink"

waitForOK "flink/" "Check for Flink response"

waitForOK "metrics" "Check for Kibana response"

waitForOK "search" "Check for Grafana response"

#TODO:
#check import process
#check test with test data

echo "Everything seems fine :)"
