FROM flink:1.7.2-scala_2.11

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
RUN mkdir -p /tmp/storage/1.4/blob/cache

USER root
ENTRYPOINT ["/entrypointWithIP.sh"]