#!/bin/bash -ex

cd "$(dirname "$0")"

echo "Starting to add preconfigured schemas ..."

for FILE in "../../data/schema-registry"/*; do
  if [ -f "$FILE" ]; then
    FILENAME=$(basename "$FILE")
    SCHEMA_NAME="$FILENAME-value"
    echo "Creating schema '$SCHEMA_NAME'"
    ESCAPED_CONTENT=$(awk 'BEGIN{ORS="\\n"} {gsub(/"/, "\\\"")} 1' < "$FILE")
    RESPONSE=$(
      curl -s -L -w "\n%{http_code}" \
        -XPOST "http://schema-registry:8081/subjects/${SCHEMA_NAME}/versions" \
        -H "Content-Type: application/vnd.schemaregistry.v1+json" -d \
         "{
          \"schema\": \"${ESCAPED_CONTENT}\",
          \"schemaType\": \"JSON\",
          \"references\": []
        }"
    )

    HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)

    if [[ "$HTTP_CODE" != 200 ]] ; then
      RESPONSE_BODY=$(echo "$RESPONSE" | sed \$d)
      echo "Cannot add schema from $FILENAME.\nHTTP response: $RESPONSE_BODY"
      exit 1
    fi
  fi
done

echo "DONE!"
