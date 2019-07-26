#!/usr/bin/env bash

scala_version=$1

set -e

if [ "$COVERAGE" = true ]; then
    sbt ++$scala_version clean coverage test coverageReport
    sbt ++$scala_version coverageAggregate
else
    sbt ++$scala_version clean test
fi