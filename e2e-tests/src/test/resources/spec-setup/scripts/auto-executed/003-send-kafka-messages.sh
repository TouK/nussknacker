#!/bin/bash -ex

cd "$(dirname "$0")"

echo "Starting to send preconfigured messages ..."

for FILE in "../../data/kafka/messages"/*; do
  if [ -f "$FILE" ]; then
    TOPIC_NAME=$(basename "$FILE")

    while IFS= read -r MSG; do
      if [[ $MSG == "#"* ]]; then
        continue
      fi

      echo "Sending message $MSG to '$TOPIC_NAME'"
      ../utils/kafka/send-to-kafka.sh "$TOPIC_NAME" "$MSG"
    done < "$FILE"
  fi
done

echo "DONE!"
