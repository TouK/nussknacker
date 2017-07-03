cd server
java -Dlogback.configurationFile=./logback.xml -Dconfig.file=./develConf/application.conf -jar ./target/scala-2.11/esp-ui-assembly-0.1-SNAPSHOT.jar 8081 ./develConf/jsons