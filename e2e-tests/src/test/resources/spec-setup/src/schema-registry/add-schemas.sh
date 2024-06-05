#!/bin/bash -ex

for FILE in "/app/data/schema-registry"/*; do
  if [ -f "$FILE" ]; then
    FILENAME=$(basename "$FILE")
    ESCAPED_CONTENT=$(awk '{gsub(/\n/, "\\n"); gsub(/"/, "\\\""); print}' < "$FILE")

    RESPONSE=$(
      curl -s -L -w "\n%{http_code}" \
        -XPOST "http://schema-registry:8081/subjects/${FILENAME}-value/versions" \
        -H "Content-Type: application/vnd.schemaregistry.v1+json" -d \
         "{
          \"schema\": \"${ESCAPED_CONTENT}\",
          \"schemaType\": \"JSON\",
          \"references\": []
        }"
    )

    HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
    RESPONSE_BODY=$(echo "$RESPONSE" | sed \$d)

    if [[ "$HTTP_CODE" == 200 ]] ; then
      echo "Cannot add schema from $FILENAME"
      exit 1
    fi
  fi
done
