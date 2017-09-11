#!/bin/bash

while [ true ]
do
sleep 0.1
ID=$((1 + RANDOM % 5))
AMOUNT=$((1 + RANDOM % 30))
NOW=`date +%s%3N`
TIME=$((NOW - RANDOM % 20))
echo "{ \"clientId\": \"Client$ID\", \"amount\": $AMOUNT, \"eventDate\": $TIME}" 
done | ./sendToKafka.sh transactions
