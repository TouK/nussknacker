FROM flink:1.3.1
ADD ./additional/* /opt/flink/lib/
#files have user root...
USER root
RUN mkdir -p /opt/flinkData/savepoints
RUN mkdir -p /opt/flinkData/checkpoints
