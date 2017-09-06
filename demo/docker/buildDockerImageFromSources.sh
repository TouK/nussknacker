#!/usr/bin/env bash
source ./.env
DOCKER_APP_LOCATION="./app/build/"
if [ -z ${CODE_LOCATION} ]; then
    echo "undefined docker-compose variable CODE_LOCATION"
    exit -1
fi
buildModule(){
    local module=$1
    cd ../../
    ./sbtwrapper "${module}/clean" "${module}/assembly"
    cd -
}
findJar(){
    local module=$1
    echo $(find ../.. -name "nussknacker-${module}-assembly*.jar")
}
buildSampleAssembly(){
    echo "Building current sample app"
    buildModule example
    local FILE=$(findJar example)
    echo "Using file $FILE"
    cp ${FILE} ${CODE_LOCATION}
}

echo "Building current sample app"
buildModule example
SAMPLE_JAR=$(findJar example)
cp ${SAMPLE_JAR} ${CODE_LOCATION}

echo "Building Nussknacker UI"
buildModule ui
UI_JAR=$(findJar ui)
cp ${UI_JAR} ${DOCKER_APP_LOCATION}

docker-compose build --no-cache app
rm "${DOCKER_APP_LOCATION}/nussknacker-ui-assembly.jar"