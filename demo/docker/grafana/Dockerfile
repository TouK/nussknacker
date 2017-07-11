FROM grafana/grafana:4.4.1
ADD ./runWithFlinkEspBoard.sh /
ADD ./Flink-ESP.json /

ENTRYPOINT ["/runWithFlinkEspBoard.sh"]
