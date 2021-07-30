#!/usr/bin/env bash

set -e

sudo chown root:docker /var/lib/docker
sudo chown root:docker /var/lib/docker/volumes
sudo mkdir /opt/flink/data/ -p
sudo ln -s /var/lib/docker/volumes/docker_nussknacker_storage_flink/_data/savepoints /opt/flink/data
