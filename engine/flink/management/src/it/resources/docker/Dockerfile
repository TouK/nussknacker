FROM flink:1.9.1-scala_${scala.major.version}

COPY entrypointWithIP.sh /
COPY docker-entrypoint.sh /
COPY conf.yml /

#TODO: figure out users...

USER root
RUN chown flink /entrypointWithIP.sh

RUN chown flink /conf.yml
RUN chmod +x /entrypointWithIP.sh
RUN chmod +x /docker-entrypoint.sh


USER flink
RUN mkdir -p /tmp/storage

USER root
ENTRYPOINT ["/entrypointWithIP.sh"]