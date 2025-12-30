FROM flink:1.20.3-java17

COPY entrypointWithIP.sh /
COPY conf.yml /
COPY log4j-console.properties /

#TODO: figure out users...

USER root
RUN chown flink /entrypointWithIP.sh

RUN chown flink /conf.yml
RUN chown flink /log4j-console.properties
RUN chmod +x /entrypointWithIP.sh

USER flink
RUN mkdir -p /tmp/storage

USER root
ENTRYPOINT ["/entrypointWithIP.sh"]
