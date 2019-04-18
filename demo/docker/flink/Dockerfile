FROM flink:1.7.2-scala_2.11
ADD ./additional/* /opt/flink/lib/
ADD ./run.sh /run.sh
#files have user root...
USER root
RUN mkdir -p /opt/flinkData/savepoints
RUN mkdir -p /opt/flinkData/checkpoints
RUN mkdir -p /opt/flinkData/storage

ENTRYPOINT ["/run.sh"]

