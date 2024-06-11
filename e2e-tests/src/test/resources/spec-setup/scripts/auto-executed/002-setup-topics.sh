#!/bin/bash -ex

cd "$(dirname "$0")"

while IFS= read -r TOPIC_NAME; do

  if [[ $TOPIC_NAME == "#"* ]]; then
    continue
  fi

  echo "Creating topic '$TOPIC_NAME'"
  /opt/bitnami/kafka/bin/kafka-topics.sh --create --bootstrap-server kafka:9092  --topic "$TOPIC_NAME"
done < "/app/data/kafka/topics"
