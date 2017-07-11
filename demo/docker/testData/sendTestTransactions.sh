#!/bin/bash
cat ./transactions.json | ./sendToKafka.sh transactions
