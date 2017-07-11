#!/bin/bash
#Inspired by https://github.com/tutumcloud/influxdb/blob/master/1.0/run.sh and https://github.com/grafana/grafana-docker/issues/11

#To be able to move to foreground
set -m

./run.sh "${@}" &

#Waiting for start of grafana
RET=1
while [[ RET -ne 0 ]]; do
    echo "=> Waiting for confirmation of grafana service startup ..."
    sleep 3
    curl -k localhost:3000 2> /dev/null
    RET=$?
done
echo ""

#We add datasource
curl -s -H "Content-Type: application/json" \
    -XPOST http://admin:admin@localhost:3000/api/datasources \
    -d @- <<EOF
{
    "name": "influx",
    "type": "influxdb",
    "access": "proxy",
    "url": "http://influxdb:8086",
    "database": "esp"
}
EOF

#We import our dashboard
curl -H "Content-Type: application/json" \
    -XPOST http://admin:admin@localhost:3000/api/dashboards/import \
    -d@/Flink-ESP.json

#We move grafana process to foreground
fg