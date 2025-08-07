ARG FLINK_VERSION

FROM flink:${FLINK_VERSION}

USER root
RUN echo '#!/bin/sh' > /ex-docker-entrypoint.sh && \
    echo 'export FLINK_PROPERTIES=$(cat /opt/flink/conf/flink-properties.yml) && /docker-entrypoint.sh "$@"' >> /ex-docker-entrypoint.sh && \
    chmod +x /ex-docker-entrypoint.sh

USER flink
COPY flink-properties.yml /opt/flink/conf/
RUN mkdir -p /opt/flink/data && \
    chmod -R 777 /opt/flink/data

VOLUME /opt/flink/data

ENTRYPOINT [ "/ex-docker-entrypoint.sh" ]
