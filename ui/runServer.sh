cd server
java -Dlogback.configurationFile=./logback.xml -Dconfig.file=./develConf/application.conf -jar ./target/scala-2.11/nussknacker-ui-assembly.jar 8081 ./develConf/jsons