#!/bin/bash
PROCESS=influx
if [ -f "$PROCESS.pid" ]
then
    cat $PROCESS.pid | xargs kill
fi
nohup ~/influxdb-0.10.0-1/usr/bin/influxd -config ~/influxdb.conf >> /tmp/${PROCESS}.out &
echo $! > ${PROCESS}.pid