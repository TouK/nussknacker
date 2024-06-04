#!/bin/bash -ex

for FILE in "/app/data/schema-registry"/*; do
  if [ -f "$FILE" ]; then
    FILENAME=$(basename "$FILE")
    ESCAPED_CONTENT=$(awk '{gsub(/\n/, "\\n"); gsub(/"/, "\\\""); print}' < "$FILE")

    HTTP_STATUS=$(
      curl -o /dev/null -s -w "%{http_code}\n" \
        -XPOST "http://schema-registry:8081/subjects/${FILENAME}-value/versions" \
        -H "Content-Type: application/vnd.schemaregistry.v1+json" -d \
         "{
          \"schema\": \"${ESCAPED_CONTENT}\",
          \"schemaType\": \"JSON\",
          \"references\": []
        }"
    )

    if [[ "$HTTP_STATUS" -ne 200 ]] ; then
      echo "Cannot add schema from $FILENAME"
      exit 1
    fi
  fi
done
