FROM openjdk:8-jdk

ADD build/* ./

ARG version=

RUN ./prepare.sh $version

VOLUME /db

CMD exec java -Dlogback.configurationFile=./conf/logback.xml -Dconfig.file=./conf/application.conf -cp nussknacker-ui-assembly.jar pl.touk.esp.ui.EspUiApp 8080 jsons
