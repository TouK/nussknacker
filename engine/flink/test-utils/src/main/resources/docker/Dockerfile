FROM flink:1.19.1-scala_2.12-java11

COPY entrypointWithIP.sh /
COPY conf.yml /
COPY log4j-console.properties /

#TODO: figure out users...

USER root
RUN chown flink /entrypointWithIP.sh

RUN chown flink /conf.yml
RUN chown flink /log4j-console.properties
RUN chmod +x /entrypointWithIP.sh

${scala.version.flink.tweak.commands}

USER flink
RUN mkdir -p /tmp/storage

USER root
ENTRYPOINT ["/entrypointWithIP.sh"]
