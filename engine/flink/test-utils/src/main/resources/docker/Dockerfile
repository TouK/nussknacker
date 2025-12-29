FROM touk/flink:1.1.5-flink1.20.2-scala_${scala.version}

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
