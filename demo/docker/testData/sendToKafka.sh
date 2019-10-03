#!/bin/bash
#TODO: is it always this?
CONTAINER_NAME=nussknacker_kafka
docker exec -i $CONTAINER_NAME "/opt/kafka_2.12-2.2.0/bin/kafka-console-producer.sh" --topic $1 --broker-list localhost:9092

