#!/bin/bash

#TODO: get rid of it
mkdir -p /tmp/$SAVEPOINT_DIR_NAME
chmod -R 777 /tmp/$SAVEPOINT_DIR_NAME

cat /conf.yml >> $FLINK_HOME/conf/flink-conf.yaml
cp ${FLINK_HOME}/opt/flink-queryable-state-runtime*.jar ${FLINK_HOME}/lib

/docker-entrypoint.sh "$@"