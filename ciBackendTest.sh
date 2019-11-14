#!/usr/bin/env bash

if [ -z "$1" ]; then
    scala_version="+"
else
    scala_version="++$1"
fi

set -e
echo $scala_version

if [ "$COVERAGE" = true ]; then
    sbt $scala_version clean coverage test coverageReport
    sbt $scala_version coverageAggregate
else
    sbt $scala_version clean test
fi