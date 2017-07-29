#!/usr/bin/env bash
echo "Usage: ./getSampleAssembly.sh [VERSION]. If no version is given, sample app will be built from source"
VERSION=$1

if [[ -z "${VERSION}" ]]; then
    echo "Building current sample app"
    cd ../../
    ./sbtwrapper example/clean example/assembly
    cd -
    FILE=`find ../../engine -name 'nussknacker-example-assembly-*'`
    echo "Using file $FILE"
    cp ${FILE} /tmp/code-assembly.jar
else
    echo "Downloading example model in version $VERSION"
    if [[ "$VERSION" == *-SNAPSHOT ]]; then
        #TODO: what will be default snapshots location??
       REPO=https://philanthropist.touk.pl/nexus/content/repositories/snapshots
    else
       REPO=https://repo1.maven.org/maven2
    fi
    wget -O /tmp/code-assembly.jar ${REPO}/pl/touk/nussknacker/nussknacker-example_2.11/${VERSION}/nussknacker-example_2.11-${VERSION}-assembly.jar
fi
