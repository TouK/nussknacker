#!/usr/bin/env bash
#this is needed to access checkpoint data in shared volume
chmod -R 777 /opt/flinkData
exec java -Dlogback.configurationFile=./conf/logback.xml -Dconfig.file=./conf/application.conf -cp nussknacker-ui-assembly.jar pl.touk.nussknacker.ui.NussknackerApp 8080