#!/bin/bash
if [[ -z "$JOB_MANAGER_RPC_ADDRESS" && -n "$JOB_MANAGER_RPC_ADDRESS_COMMAND" ]]; then
    export JOB_MANAGER_RPC_ADDRESS=$(eval $JOB_MANAGER_RPC_ADDRESS_COMMAND)
fi
echo "Using JOB_MANAGER_RPC_ADDRESS: $JOB_MANAGER_RPC_ADDRESS"

mkdir -p /tmp/$SAVEPOINT_DIR_NAME
chmod -R 777 /tmp/$SAVEPOINT_DIR_NAME

cat /conf.yml | sed s/SAVEPOINT_DIR_NAME/$SAVEPOINT_DIR_NAME/ >> $FLINK_HOME/conf/flink-conf.yaml

cp ${FLINK_HOME}/opt/flink-queryable-state-runtime*.jar ${FLINK_HOME}/lib

/docker-entrypoint.sh "$@"