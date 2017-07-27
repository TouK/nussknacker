#!/bin/bash
cd $(dirname $0)
cat ./transactions.json | ./sendToKafka.sh transactions
