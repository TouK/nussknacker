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

#TODO: only temporary, we should have just one sbt project
cd engine
runAndExitOnFail "./ciBuild.sh $espEngineToukVersion"
cd -
cd ui
runAndExitOnFail "./ciBuild.sh $espEngineToukVersion"
cd -
