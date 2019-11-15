#!/usr/bin/env bash

set -e

espEngineToukVersion=$1
nexusPassword=$2

if [[ -n "$3" ]]; then
    nexusUrlProperty="-DnexusUrl=$3"
else
    nexusUrlProperty=""
fi

if [[ -n "$4" ]]; then
    nexusUserProperty="-DnexusUser=$4"
else
    nexusUserProperty=""
fi

cd ui/client && npm ci && cd -
./sbtwrapper -DnexusPassword=$2 ${nexusUrlProperty} ${nexusUserProperty} "set version in ThisBuild := \"$espEngineToukVersion\"" +publish
