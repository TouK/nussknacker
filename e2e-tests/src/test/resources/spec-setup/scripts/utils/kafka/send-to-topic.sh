#!/bin/bash -e

if [ "$#" -ne 2 ]; then
    echo "Two parameters required: 1) topic name, 2) message"
    exit 1
fi

cd "$(dirname "$0")"

TOPIC=$1
MESSAGE=$2

echo "$MESSAGE" | /opt/bitnami/kafka/bin/kafka-console-producer.sh --topic "$TOPIC" --bootstrap-server kafka:9092
