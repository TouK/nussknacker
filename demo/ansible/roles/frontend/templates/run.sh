#!/usr/bin/env bash
#bez shebanga ansible sie pluje przy wolaniu skryptu

if [ -a frontend.pid ]
then
  kill `cat frontend.pid`
fi
nohup java -Dlogback.configurationFile=./logback.xml -Dconfig.file=application.conf -cp {{flink_dir}}/lib/*:./esp-ui-assembly-{{nussknacker_version}}.jar pl.touk.esp.ui.EspUiApp 8080 jsons &> frontend.log &
echo $! > frontend.pid
