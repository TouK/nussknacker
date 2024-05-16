ARG FLINK_VERSION

FROM apache/flink:${FLINK_VERSION}
USER flink
RUN mkdir -p /opt/flink/data
RUN chmod -R 777 /opt/flink/data
VOLUME /opt/flink/data
