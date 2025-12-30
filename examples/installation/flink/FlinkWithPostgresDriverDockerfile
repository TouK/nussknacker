ARG FLINK_VERSION

FROM curlimages/curl:8.9.1 AS lib_provider

# Adding custom libraries ('add other libraries' section):
# https://nightlies.apache.org/flink/flink-docs-master/docs/deployment/resource-providers/standalone/docker/#further-customization
WORKDIR /libs
RUN curl -k --output /libs/postgresql-42.6.0.jar https://repo1.maven.org/maven2/org/postgresql/postgresql/42.6.0/postgresql-42.6.0.jar

FROM flink:${FLINK_VERSION}

USER root
RUN echo '#!/bin/sh' > /ex-docker-entrypoint.sh && \
    echo 'export FLINK_PROPERTIES=$(cat /opt/flink/conf/flink-properties.yml) && /docker-entrypoint.sh "$@"' >> /ex-docker-entrypoint.sh && \
    chmod +x /ex-docker-entrypoint.sh
COPY --from=lib_provider /libs/ /opt/flink/lib/

USER flink
COPY flink-properties.yml /opt/flink/conf/
RUN mkdir -p /opt/flink/data && \
    chmod -R 777 /opt/flink/data

VOLUME /opt/flink/data

ENTRYPOINT [ "/ex-docker-entrypoint.sh" ]
