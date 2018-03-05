#!/usr/bin/env bash
chown -R flink:flink /opt/flinkData
chmod -R 777 /opt/flinkData
/docker-entrypoint.sh $@