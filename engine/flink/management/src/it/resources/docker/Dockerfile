FROM flink:1.3.1

COPY entrypointWithIP.sh /
COPY conf.yml /

#TODO: figure out users...

USER root
RUN chown flink /entrypointWithIP.sh
RUN chown flink /conf.yml
RUN chmod +x /entrypointWithIP.sh

USER flink
RUN mkdir -p /tmp/storage
RUN mkdir -p /tmp/storage/1.3/blob/cache

USER root
ENTRYPOINT ["/entrypointWithIP.sh"]