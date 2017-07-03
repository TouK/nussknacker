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

cd client
echo "npm --version"
npm --version
echo "node --version"
node --version
npm install
runAndExitOnFail "npm test"
runAndExitOnFail "npm run build"
cd -

cd server
#FIXME: na razie nie potrafie inaczej...
rm -rf ~/.ivy2/cache/pl.touk.esp/esp-management-sample_2.11

if [ -z "$espEngineToukVersion" ]
    then
        ./sbtwrapper clean test
    else
        ./sbtwrapper clean test -DespEngineToukVersion=$espEngineToukVersion
fi
