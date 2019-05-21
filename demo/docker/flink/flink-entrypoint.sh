#!/usr/bin/env bash

BASE_DIR="/opt/flinkData"

mkdir -p ${BASE_DIR}/checkpoints
mkdir -p ${BASE_DIR}/savepoints
mkdir -p ${BASE_DIR}/storage

chown -R flink:flink BASE_DIR
chmod -R 777 BASE_DIR

/docker-entrypoint.sh $@
