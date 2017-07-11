#!/bin/bash
#TODO: is it always this?
CONTAINER_NAME=docker_kafka_1
docker exec -i $CONTAINER_NAME "/opt/kafka_2.12-0.10.2.1/bin/kafka-console-producer.sh" --topic $1 --broker-list localhost:9092

