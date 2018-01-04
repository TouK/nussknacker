FROM openjdk:8-jdk

ADD build/* ./

ARG version=

RUN ./prepare.sh $version

VOLUME /db

#last argument is for compatibility with version 0.0.7, it'll be removed it next versions
CMD exec java -Dlogback.configurationFile=./conf/logback.xml -Dconfig.file=./conf/application.conf -cp nussknacker-ui-assembly.jar pl.touk.nussknacker.ui.NussknackerApp 8080 .
