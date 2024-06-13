#!/bin/bash -ex

cd "$(dirname "$0")"

function sendMessage() {
  if [ "$#" -ne 2 ]; then
    echo "Error: Two parameters required: 1) topic name, 2) message"
    exit 11
  fi

  set -e

  local TOPIC_NAME=$1
  local MSG=$2

  echo "Sending message $MSG to '$TOPIC_NAME'"
  ../utils/kafka/send-to-topic.sh "$TOPIC_NAME" "$MSG"
  echo "Message sent!"
}

echo "Starting to send preconfigured messages ..."

for FILE in "../../data/kafka/messages"/*; do
  if [ -f "$FILE" ]; then
    TOPIC_NAME=$(basename "$FILE")

    while IFS= read -r MSG; do
      if [[ $MSG == "#"* ]]; then
        continue
      fi

      sendMessage "$TOPIC_NAME" "$MSG"
    done < "$FILE"
  fi
done

echo "DONE!"
