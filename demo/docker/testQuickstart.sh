#!/usr/bin/env bash

set -e

echo "Starting docker containers.."

#just in case
docker-compose -f docker-compose.yml -f docker-compose-env.yml kill
docker-compose -f docker-compose.yml -f docker-compose-env.yml rm -f -v
docker-compose -f docker-compose.yml -f docker-compose-env.yml build
docker-compose -f docker-compose.yml -f docker-compose-env.yml up -d --no-recreate

trap 'docker-compose -f docker-compose.yml -f docker-compose-env.yml kill && docker-compose -f docker-compose.yml -f docker-compose-env.yml rm -f -v' EXIT

#TODO: Consider rewriting below, e.g. in Python
waitTime=0
sleep=10
waitLimit=120

checkCode() {
 echo "$(curl -s -o /dev/null -w "%{http_code}" "http://admin:admin@localhost:8081/$1")"
}

waitForOK() {
  echo "$2"

  URL_PATH=$1
  STATUS_CODE=$(checkCode "$URL_PATH")

  while [[ $waitTime < $waitLimit && $STATUS_CODE != 200 ]]
  do
    sleep $sleep
    waitTime=$((waitTime+sleep))
    STATUS_CODE=$(checkCode "$URL_PATH")

    if [[ $STATUS_CODE != 200  ]]
    then
      echo "Service still not started within $waitTime sec and response code: $STATUS_CODE.."
    fi
  done
  if [[ $STATUS_CODE != 200 ]]
  then
    echo "$3"
    exit 1
  fi
}

waitForOK "api/processes" "Checking Frontend API response.." "Frontend not started"

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

waitForOK "api/processes/status" "Checking connect with Flink.." "Frontend not connected with flink"

waitForOK "flink/" "Checking Flink response.." "Flink not started"

waitForOK "metrics" "Checking Grafana response.." "Grafana not started"

waitForOK "search" "Checking Kibana response.." "Kibana not started"

#TODO:
#check import process
#check test with test data

echo "Everything seems fine :)"
