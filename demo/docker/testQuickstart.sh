#!/usr/bin/env bash
set -e
echo "Downloading sample"
./downloadSampleAssembly.sh

echo "Starting containers"
#just in case
docker-compose kill
docker-compose rm -f -v
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
    OK="OK"
  else
    OK=""
  fi
}

echo "Waiting for frontend to start"

while [[ $waitTime < 60 && $OK == "" ]]
do
  echo "Still not started..."
  sleep $sleep
  waitTime=$(($waitTime+$sleep))
  checkCode "api/processes"
done

if [[ $OK == "" ]]
then
  echo "Containers not started"
  exit 1
fi

echo "Creating process"
CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "http://admin:admin@localhost:8081/api/processes/DetectLargeTransactions/FraudDetection?isSubprocess=false")
if [[ $CODE != 201 ]]
then
  echo "Process creation failed with $CODE"
  exit 1
fi

#TODO:
#check import process
#check test with test data

echo "Everything seems fine :)"
