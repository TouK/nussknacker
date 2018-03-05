FROM openjdk:8-jdk

ADD build/* ./

ARG version=

RUN ./prepare.sh $version

VOLUME /db

CMD /run.sh
