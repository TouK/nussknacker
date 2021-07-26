#!/bin/bash

set -e

cd "$(dirname $0)"

cat ./transactions.json | ./sendToKafka.sh transactions
