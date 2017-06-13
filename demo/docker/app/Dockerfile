FROM openjdk:8-jdk

ADD build/* ./

VOLUME /db

CMD exec java -Dlogback.configurationFile=./conf/logback.xml -Dconfig.file=./conf/application.conf -cp esp-ui-assembly-0.1-SNAPSHOT.jar pl.touk.esp.ui.EspUiApp 8080 jsons
