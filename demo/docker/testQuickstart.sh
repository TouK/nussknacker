#!/usr/bin/env bash
echo "Downloading sample"
./downloadSampleAssembly.sh

echo "Starting containers"
#just in case
docker-compose kill
docker-compose rm -f -v
docker-compose build
docker-compose up -d --no-recreate

trap 'docker-compose kill && docker-compose rm -f -v' EXIT

#TODO: Consider rewriting below, e.g. in Python
waitTime=0
sleep=5

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
  while [[ $waitTime < 60 && $OK == 1 ]]
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
if [[ $CODE != 201 ]]
then
  echo "Process creation failed with $CODE"
  exit 1
fi

waitForOK "api/processes/status" "Cannot connect with Flink"

#TODO:
#check import process
#check test with test data

echo "Everything seems fine :)"
