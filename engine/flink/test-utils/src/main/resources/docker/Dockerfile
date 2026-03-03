FROM touk/flink:1.2.1-flink1.20.3-scala_${scala.version}

COPY entrypointWithIP.sh /
COPY config.overrides.yml /
COPY log4j-console.overrides.properties /

USER root
RUN chmod +x /entrypointWithIP.sh

USER flink
RUN mkdir -p /tmp/storage

USER root
ENTRYPOINT ["/entrypointWithIP.sh"]
