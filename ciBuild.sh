#!/usr/bin/env bash
espEngineToukVersion=$1
if [ -z "$espEngineToukVersion" ]
    then
        ./sbtwrapper clean test management/it:test
    else
        ./sbtwrapper clean test management/it:test
        ./sbtwrapper publish -DespEngineToukVersion=$espEngineToukVersion
fi