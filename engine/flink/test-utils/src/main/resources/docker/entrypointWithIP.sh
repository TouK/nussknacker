#!/bin/bash

#TODO: get rid of it
set -eu
if [[ -n "${SAVEPOINT_DIR_PATH:-}" ]]; then
  mkdir -p "$SAVEPOINT_DIR_PATH"
  chmod -R 777 "$SAVEPOINT_DIR_PATH"
fi
mkdir -p /output
chmod -R 777 /output

cat /config.overrides.yml >> $FLINK_HOME/conf/config.yaml
cat /log4j-console.overrides.properties >> $FLINK_HOME/conf/log4j-console.properties

exec /docker-entrypoint.sh "$@"
