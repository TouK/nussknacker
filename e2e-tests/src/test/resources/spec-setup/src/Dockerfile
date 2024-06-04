FROM ubuntu:20.04

WORKDIR /app

COPY entrypoint.sh /
COPY src/ /app/scripts/
COPY data/ /app/scripts/

ENTRYPOINT [ "/entrypoint.sh" ]
