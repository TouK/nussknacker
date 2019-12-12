#!/usr/bin/env bash
#TODO maybe use sbt-native-packager scripts generator?

NUSSKNACKER_DIR=`dirname "$0" | xargs -I{} readlink -f {}/..`
PID_FILE="$NUSSKNACKER_DIR/frontend.pid"

if [ -a $PID_FILE ]
then
  PID=$(cat $PID_FILE)
  kill $PID
  echo "=> Waiting for $PID to stop..."
  tail --pid=$PID -f /dev/null
fi

set -e

cd $NUSSKNACKER_DIR
RUN_IN_BACKGROUND=true ./bin/run.sh