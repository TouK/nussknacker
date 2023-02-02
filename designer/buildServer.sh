#!/usr/bin/env bash

set -e

cd "$(dirname -- "$0")"/..
addManagerArtifacts=true sbt "designer/assembly; prepareDev"
