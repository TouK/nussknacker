#!/bin/bash

#for testing docker-compose

set -e

cd "$(dirname $0)"

echo "Restarting docker containers"

docker-compose -f docker-compose.yml -f docker-compose-env.yml stop
docker-compose -f docker-compose.yml -f docker-compose-env.yml rm -f -v
docker-compose -f docker-compose.yml -f docker-compose-env.yml build
docker-compose -f docker-compose.yml -f docker-compose-env.yml up -d --no-recreate --remove-orphans
