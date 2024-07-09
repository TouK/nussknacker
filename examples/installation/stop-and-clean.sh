#!/bin/bash -e

cd "$(dirname "$0")"

echo "Running Nussknacker installation example clean up ..."
echo ""

if ! docker version &>/dev/null; then
  echo "No Docker found. Docker is required to run this example. See https://docs.docker.com/engine/install/"
  exit 1
fi

if ! docker compose version &>/dev/null; then
  echo "No docker compose found. It seems you have to upgrade your Docker installation. See https://docs.docker.com/engine/install/"
  exit 2
fi

if ! docker compose config > /dev/null; then
  echo "Cannot validate docker compose configuration. It seems you have to upgrade your Docker installation. See https://docs.docker.com/engine/install/"
  exit 3
fi

docker compose down -v

echo "All is cleaned. Goodbye"
