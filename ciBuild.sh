#!/usr/bin/env bash
espEngineToukVersion=$1
nexusPassword=$2

if [ -n "$3" ]; then
    nexusUrlProperty="-DnexusUrl=$3"
else
    nexusUrlProperty=""
fi

if [ -n "$4" ]; then
    nexusUserProperty="-DnexusUser=$4"
else
    nexusUserProperty=""
fi

./sbtwrapper clean test management/it:test || { echo 'Failed to build and test nussknacker' ; exit 1; }
if [ -n "$espEngineToukVersion" ]; then
    ./sbtwrapper -DnexusPassword=$2 ${nexusUrlProperty} ${nexusUserProperty} "set version in ThisBuild := \"$espEngineToukVersion\"" publish
fi
