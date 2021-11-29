#!/usr/bin/env bash

NUSSKNACKER_DIR=`dirname "$0" | xargs -I{} readlink -f {}/..`
export STORAGE_DIR="$NUSSKNACKER_DIR/storage"
CONF_DIR="$NUSSKNACKER_DIR/conf"
LIB_DIR="$NUSSKNACKER_DIR/lib"
MODELS_DIR="$NUSSKNACKER_DIR/models"

CONFIG_FILE=${CONFIG_FILE-${2-"$CONF_DIR/application.conf"}}
LOG_FILE=${NUSSKNACKER_LOG_FILE-${3-"$CONF_DIR/docker-logback.xml"}}
APPLICATION_APP=${NUSSKNACKER_APPLICATION_APP-${4-"pl.touk.nussknacker.engine.requestresponse.http.RequestResponseHttpApp"}}
USER=${DAEMON_USER-${5-"daemon"}}
GROUP=${DAEMON_GROUP-${6-"daemon"}}

mkdir -p ${STORAGE_DIR}/logs

chmod -R ug+wr ${STORAGE_DIR}

echo "Nussknacker request-response up and running with" \
     "CONFIG: $CONFIG_FILE," \
     "LOG: $LOG_FILE," \
     "APP: $APPLICATION_APP," \
     "MODELS: $MODELS_DIR," \
     "USER: $USER," \
     "GROUP: $GROUP."

exec java $JDK_JAVA_OPTIONS -Dlogback.configurationFile="$LOG_FILE" \
          -Dconfig.file="$CONFIG_FILE" -Dconfig.override_with_env_vars=true \
          -cp "$LIB_DIR/*:$MANAGERS_DIR/*:$MODELS_DIR/*" "$APPLICATION_APP"
