#!/bin/bash
PROCESS=grafana
if [ -f "$PROCESS.pid" ]
then
    cat $PROCESS.pid | xargs kill
fi
#TODO: dlaczego to musi byc z tego katalogu??
cd {{grafana_dir}}
nohup ./bin/grafana-server --config ~/grafana.ini &>> /tmp/${PROCESS}.out &
echo $! > ~/${PROCESS}.pid
cd ~