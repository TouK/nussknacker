#!/bin/sh
#skrypt do testu czy frontend poprawnie buduje sie w trybie produkcyjnym
./build.sh
java -Dconfig.file=./server/develConf/application.conf -jar ./server/target/scala-2.11/esp-ui-assembly-0.1-SNAPSHOT.jar 8081 ./server/develConf/jsons