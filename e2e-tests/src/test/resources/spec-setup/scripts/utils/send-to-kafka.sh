#!/bin/bash -ex

if [ "$#" -ne 2 ]; then
    echo "The scripts requires two params: topic name and message to send"
    exit 1
fi

TOPIC_NAME=$1
MESSAGE_TO_SEND=$1

echo "$MESSAGE_TO_SEND" | ./opt/bitnami/kafka/bin/kafka-console-producer.sh --topic "$TOPIC_NAME" --bootstrap-server kafka:9092
