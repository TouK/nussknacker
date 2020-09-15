#!/usr/bin/env bash

DATA_DIR=${FLINK_DATA-"$FLINK_HOME/data"}
GROUP=${DAEMON_GROUP-"flink"}
USER=${DAEMON_USER-"flink"}

cp ${FLINK_HOME}/opt/flink-queryable-state-runtime_${SCALA_VERSION}-${FLINK_VERSION}.jar ${FLINK_HOME}/lib/flink-queryable-state-runtime_${SCALA_VERSION}-${FLINK_VERSION}.jar

mkdir -p "$DATA_DIR/checkpoints" "$DATA_DIR/savepoints" "$DATA_DIR/storage" "$DATA_DIR/logs"
chown -R ${USER}:${GROUP} ${DATA_DIR}
chmod -R 777 ${DATA_DIR}

/docker-entrypoint.sh $@
