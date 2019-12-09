#!/usr/bin/env bash
#TODO we assume running from nussknacker dir for now - this assumption is held in classpath conf - classpath: ["model/genericModel.jar"]
#TODO handle JAVA_OPTS etc. - maybe use sbt-native-packager scripts generator?

NUSSKNACKER_DIR=`dirname "$0" | xargs -I{} readlink -f {}/..`
export STORAGE_DIR="$NUSSKNACKER_DIR/storage"
CONF_DIR="$NUSSKNACKER_DIR/conf"
LIB_DIR="$NUSSKNACKER_DIR/lib"

LOGS_DIR="$NUSSKNACKER_DIR/logs"
LOG_FILE="$LOGS_DIR/frontend.log"
PID_FILE="$NUSSKNACKER_DIR/frontend.pid"

if [[ "${USE_DOCKER_ENV}" == "true" ]]; then
  echo "Using envirnoment from docker"
  export DEVELOPMENT_MODE="true"
  # See demo/docker/docker-compose-env.yml - mapped port from docker
  export GRAFANA_URL="http://localhost:8081/grafana"
  export KIBANA_URL="http://localhost:8081/kibana"
  export FLINK_REST_URL="http://localhost:3031"
  export FLINK_QUERYABLE_STATE_PROXY_URL="localhost:3063"
  export KAFKA_ADDRESS="localhost:3032"
elif [[ "${PROXY_URL}" != "" ]]; then
  export PROXY_URL
else
  # PROXY_URL is not set up so we assume that CORS should be enabled
  export DEVELOPMENT_MODE="true"
  export GRAFANA_URL=${GRAFANA_URL:-http://localhost:3000}
  export KIBANA_URL=${KIBANA_URL:-http://localhost:5601}
  export FLINK_REST_URL=${FLINK_REST_URL:-http://localhost:8081}
  export FLINK_QUERYABLE_STATE_PROXY_URL=${FLINK_QUERYABLE_STATE_PROXY_URL:-localhost:9069}
  export KAFKA_ADDRESS=${KAFKA_ADDRESS:-localhost:9092}
fi

export AUTHENTICATION_METHOD="BasicAuth"
export AUTHENTICATION_USERS_FILE="$NUSSKNACKER_DIR/conf/users.conf"

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