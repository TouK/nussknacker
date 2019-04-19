#!/usr/bin/env bash
#TODO we assume running from nussknacker dir for now - this assumption is held in classpath conf - classpath: ["model/genericModel.jar"]
#TODO handle JAVA_OPTS etc. - maybe use sbt-native-packager scripts generator?

NUSSKNACKER_DIR=`dirname "$0" | xargs -I{} readlink -f {}/..`
LIB_DIR="$NUSSKNACKER_DIR/lib"
CONF_DIR="$NUSSKNACKER_DIR/conf"
LOGS_DIR="$NUSSKNACKER_DIR/logs"
LOG_FILE="$LOGS_DIR/frontend.log"
PID_FILE="$NUSSKNACKER_DIR/frontend.pid"

if [ -a $PID_FILE ]
then
  PID=$(cat $PID_FILE)
  kill $PID
  echo "=> Waiting for $PID to stop..."
  tail --pid=$PID -f /dev/null
fi

mkdir -p $LOGS_DIR

cd $NUSSKNACKER_DIR
exec java -Dlogback.configurationFile=$CONF_DIR/logback.xml -Dconfig.file=$CONF_DIR/application.conf -cp "$LIB_DIR/*" pl.touk.nussknacker.ui.NussknackerApp >> $LOG_FILE 2>&1 &
echo $! > $PID_FILE

echo "Nussknacker up and running"