#!/usr/bin/env bash
set -e

if [[ "$COVERAGE" = true ]]; then
    ./ciRunSbt.sh clean coverage test coverageReport
    ./ciRunSbt.sh coverageAggregate
else
    ./ciRunSbt.sh clean test
fi