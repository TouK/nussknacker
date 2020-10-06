#!/usr/bin/env bash

DATA_DIR=${FLINK_DATA-"$FLINK_HOME/data"}
GROUP=${DAEMON_GROUP-"flink"}
USER=${DAEMON_USER-"flink"}

#we use wildcards, to avoid passing flink/scala versions...
cp ${FLINK_HOME}/opt/flink-queryable-state-runtime*.jar ${FLINK_HOME}/lib
#/tmp/flink-conf.yaml is mounted to outside file, we cannot mount it directly to flink conf dir as flink entrypoint moves config files
cp /tmp/flink-conf.yaml ${FLINK_HOME}/conf/flink-conf.yaml

mkdir -p "$DATA_DIR/checkpoints" "$DATA_DIR/savepoints" "$DATA_DIR/storage" "$DATA_DIR/logs"
chown -R ${USER}:${GROUP} ${DATA_DIR}
chmod -R 777 ${DATA_DIR}

/docker-entrypoint.sh $@
