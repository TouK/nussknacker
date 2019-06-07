#!/usr/bin/env bash

scala_version=$1

set -e

cd ui/client && npm ci && cd -

sbt ++$scala_version clean coverage test coverageReport
sbt ++$scala_version coverageAggregate