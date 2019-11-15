#!/usr/bin/env bash

scala_version=""
scala_cross_build="+"

if ! [[ -z "$1" ]]; then
    scala_version="++$1"
    scala_cross_build=""
fi

set -e

if [ "$COVERAGE" = true ]; then
    sbt $scala_version ${scala_cross_build}clean ${scala_cross_build}coverage ${scala_cross_build}test ${scala_cross_build}coverageReport
    sbt $scala_version ${scala_cross_build}coverageAggregate
else
    sbt $scala_version ${scala_cross_build}clean ${scala_cross_build}test
fi