#!/bin/bash -e

if [ "$#" -ne 1 ]; then
    echo "One parameter required: 1) topic name"
    exit 1
fi

cd "$(dirname "$0")"

TOPIC_NAME=$1

kcat -C -b kafka:9092 -t "$TOPIC_NAME" -o beginning -e -q
