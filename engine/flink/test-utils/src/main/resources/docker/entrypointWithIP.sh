#!/bin/bash

#TODO: get rid of it
mkdir -p /tmp/$SAVEPOINT_DIR_NAME
chmod -R 777 /tmp/$SAVEPOINT_DIR_NAME
mkdir -p /output
chmod -R 777 /output

cat /conf.yml >> $FLINK_HOME/conf/flink-conf.yaml
cat /log4j-console.properties >> $FLINK_HOME/conf/log4j-console.properties

/docker-entrypoint.sh "$@"
