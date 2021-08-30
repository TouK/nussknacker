FROM flink:1.14.0-scala_${scala.major.version}-java11

COPY entrypointWithIP.sh /
COPY conf.yml /

#TODO: figure out users...

USER root
RUN chown flink /entrypointWithIP.sh

RUN chown flink /conf.yml
RUN chmod +x /entrypointWithIP.sh


USER flink
RUN mkdir -p /tmp/storage

USER root
ENTRYPOINT ["/entrypointWithIP.sh"]
