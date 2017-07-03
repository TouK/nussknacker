#!/usr/bin/env bash
if [ -a esp-engine-standalone.pid ]
then
  kill `cat esp-engine-standalone.pid`
fi
nohup java -Dconfig.file=application.conf -jar esp-engine-standalone-assembly-0.1-SNAPSHOT.jar 8090 8091 &> esp-engine-standalone.log &
echo $! > esp-engine-standalone.pid