#!/usr/bin/env bash
REPO_BASE_PATH=$(git rev-parse --show-toplevel)
DOCKER_DEMO_PATH="$REPO_BASE_PATH/demo/docker"


buildModule(){
    local module=$1
    cd ${REPO_BASE_PATH}
    ./sbtwrapper "${module}/clean" "${module}/assembly"
    cd -
}

findJar(){
    local module=$1
    echo $(find ${REPO_BASE_PATH} -name "nussknacker-${module}-assembly*.jar")
}

assertDirectory(){ # if docker image ran without jar docker will create directory.
    local file=$1
    if [ -d ${file} ]; then
        echo "$file is directory. Remove it my 'sudo rm -rf $file'"
        exit -1
    fi
}