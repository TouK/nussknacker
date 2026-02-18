#!/bin/bash
set -eu

if [[ -n "${SAVEPOINT_DIR_PATH:-}" ]]; then
  chmod -R 777 "$SAVEPOINT_DIR_PATH"
fi

if [[ -d /output ]]; then
  chmod -R 777 /output
fi

cat /config.overrides.yml >> $FLINK_HOME/conf/config.yaml
cat /log4j-console.overrides.properties >> $FLINK_HOME/conf/log4j-console.properties

exec /docker-entrypoint.sh "$@"
