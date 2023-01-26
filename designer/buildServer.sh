#!/usr/bin/env bash

set -e

cd "$(dirname -- "$0")"/..
prepareManagersArtifacts=true sbt "designer/assembly; prepareDev"
