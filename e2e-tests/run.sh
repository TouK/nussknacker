#!/bin/bash

docker run -v "${PWD}:/code" --network=nussknacker_e2e_network -v /var/run/docker.sock:/var/run/docker.sock nu-bats:latest test