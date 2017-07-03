#!/usr/bin/env bash
set -e

./ciBuild.sh
./sbtwrapper clean publish
