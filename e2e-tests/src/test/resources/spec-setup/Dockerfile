FROM bitnami/kafka:3.7.0

USER root

RUN apt update && \
    apt install -y curl jq uuid-runtime kafkacat && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY entrypoint.sh /
COPY scripts/ /app/scripts/
COPY data/ /app/data/

ENTRYPOINT [ "/entrypoint.sh" ]
