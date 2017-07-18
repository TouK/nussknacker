#!/bin/bash
if [[ -z "$JOB_MANAGER_RPC_ADDRESS" && -n "$JOB_MANAGER_RPC_ADDRESS_COMMAND" ]]; then
    export JOB_MANAGER_RPC_ADDRESS=$(eval $JOB_MANAGER_RPC_ADDRESS_COMMAND)
fi
echo "Using JOB_MANAGER_RPC_ADDRESS: $JOB_MANAGER_RPC_ADDRESS"

cat /conf.yml >> $FLINK_HOME/conf/flink-conf.yaml

/docker-entrypoint.sh "$@"