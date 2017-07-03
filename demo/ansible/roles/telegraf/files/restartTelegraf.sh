#!/bin/bash
PROCESS=telegraf
if [ -f "$PROCESS.pid" ]
then
    cat $PROCESS.pid | xargs kill
fi
nohup ~/telegraf/usr/bin/telegraf -config ~/telegraf.conf &>> /tmp/${PROCESS}.out &
echo $! > ${PROCESS}.pid