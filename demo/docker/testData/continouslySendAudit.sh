#!/bin/bash

set -e

cd "$(dirname $0)"

while [ true ]; do
  sleep 1
  PREFIX=`date -Iseconds`
  #pv -L 1000 -q ~/Downloads/input.json | sed s/\"transactionId\":\"/\"transactionId\":\"$PREFIX-/ | sed s/\"subscriber\":\"/\"subscriber\":\"$PREFIX-/
  cat ~/Downloads/input.json | sed s/\"transactionId\":\"/\"transactionId\":\"$PREFIX-/ | sed s/\"subscriber\":\"/\"subscriber\":\"$PREFIX-/
done | ./sendToKafka.sh audit
