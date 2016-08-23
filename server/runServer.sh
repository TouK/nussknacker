#!/usr/bin/env bash
./sbtwrapper 'set test in assembly := {}' clean assembly
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -Dconfig.file=develConf/application.conf -jar target/scala-2.11/esp-ui-assembly-0.1-SNAPSHOT.jar 8081 develConf/jsons