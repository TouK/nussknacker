#!/bin/bash

#set -e

cd "$(dirname $0)"

URL_PATH=$1
MSG_INIT=$2
MSG_FAIL=$3
CONTAINER_FOR_LOGS=$4
SLEEP=${5-5}
WAIT_LIMIT=${6-30}

checkCode() {
  curl -s -o /dev/null -w "%{http_code}" $1
}

waitTime=0
echo "$MSG_INIT"

STATUS_CODE=$(checkCode "$URL_PATH")

while [[ $waitTime -lt $WAIT_LIMIT && $STATUS_CODE != 200 ]]; do
  sleep $SLEEP
  waitTime=$((waitTime + $SLEEP))
  STATUS_CODE=$(checkCode "$URL_PATH")

  if [[ $STATUS_CODE != 200 ]]; then
    echo "Service still not started within $waitTime sec and response code: $STATUS_CODE.."
  fi
done
if [[ $STATUS_CODE != 200 ]]; then
  echo "$MSG_FAIL"
  docker-compose logs --tail=200 "$CONTAINER_FOR_LOGS"
  exit 1
fi
