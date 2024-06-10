FROM ubuntu:24.04

RUN apt update && \
    apt install -y curl && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY entrypoint.sh /
COPY scripts/ /app/scripts/
COPY data/ /app/data/

ENTRYPOINT [ "/entrypoint.sh" ]
