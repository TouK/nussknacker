#!/bin/bash -e

if [ "$#" -ne 1 ]; then
    echo "One parameter required: 1) topic name"
    exit 1
fi

cd "$(dirname "$0")"

TOPIC_NAME=$1
DELETE_TOPIC_ORDER_FILE="/tmp/delete-$TOPIC_NAME.json"

trap 'rm "$DELETE_TOPIC_ORDER_FILE"' EXIT

echo "{ \"partitions\": [{ \"topic\": \"$TOPIC_NAME\", \"partition\": 0, \"offset\": -1 }], \"version\": 1 }" > "$DELETE_TOPIC_ORDER_FILE"

/opt/bitnami/kafka/bin/kafka-delete-records.sh --bootstrap-server kafka:9092 -offset-json-file "$DELETE_TOPIC_ORDER_FILE"
