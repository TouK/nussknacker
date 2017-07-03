#!/usr/bin/env bash
espEngineToukVersion=$1

runAndExitOnFail() {
    command=$1
    $command
    result=${PIPESTATUS[0]}
    if [[ ${result} -eq 0 ]]
    then
        echo "$command SUCCESS!"
    else
        echo "$command FAILURE!"
        exit ${result}
    fi
}

runAndExitOnFail "./sbtwrapper clean test management/it:test"
if [ -n "$espEngineToukVersion" ]
    then
        ./sbtwrapper publish -DespEngineToukVersion=$espEngineToukVersion
fi