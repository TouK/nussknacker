#!/bin/bash -ex

cd "$(dirname "$0")"

echo "Starting to create preconfigured topics ..."

while IFS= read -r TOPIC_NAME; do

  if [[ $TOPIC_NAME == "#"* ]]; then
    continue
  fi

  echo "Creating topic '$TOPIC_NAME'"
  /opt/bitnami/kafka/bin/kafka-topics.sh --create --bootstrap-server kafka:9092 --topic "$TOPIC_NAME"
done < "../../data/kafka/topics"

echo "DONE!"
