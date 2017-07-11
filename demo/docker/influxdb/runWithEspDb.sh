#!/usr/bin/env bash
#Mostly took from https://github.com/tutumcloud/influxdb/blob/master/1.0/run.sh

#To be able to move to foreground
set -m

echo "Starting influx"
exec influxd &

#wait for the startup of influxdb
RET=1
while [[ RET -ne 0 ]]; do
    echo "=> Waiting for confirmation of InfluxDB service startup ..."
    sleep 3
    curl -k localhost:8086/ping 2> /dev/null
    RET=$?
done
echo ""

echo "=> Creating database: ESP"
influx -host=localhost -port=8086 -execute="create database ESP"

#We move influxdb process to foreground
fg