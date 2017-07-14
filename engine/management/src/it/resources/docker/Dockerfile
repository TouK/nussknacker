FROM flink:1.3.1
COPY entrypointWithIP.sh /
USER root
RUN chmod +x /entrypointWithIP.sh

ENTRYPOINT ["/entrypointWithIP.sh"]