#!/bin/bash

set -e
cd "$(dirname $0)"

docker build -t nu-bats:latest .

echo "Starting docker containers to test version $NUSSKNACKER_VERSION"
#just in case
docker-compose kill
docker-compose rm -f -v
docker-compose up -d --no-recreate

trap 'docker-compose kill && docker-compose rm -f -v' EXIT

./waitForOk.sh http://admin:admin@localhost:3081/api/app/buildInfo "Designer" "Designer failed to start" designer
./run.sh


