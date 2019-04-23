#!/usr/bin/env bash

dockerTagName=`echo ${DOCKER_TAG_NAME-$1} | sed 's/[^a-zA-Z0-9]/\_/g' | awk '{print tolower($0)}'`
dockerPort=${DOCKER_PORT-${2-"8080"}}
dockerUsername=${DOCKER_PACKAGE_USERNAME-${3-"touk"}}
dockerPackageName=${DOCKER_PACKAGENAME-${4-"nussknacker"}}

if [[ -n "$dockerTagName" ]]; then
    echo "Prepare docker build for tag: $dockerTagName"

    ./sbtwrapper -DdockerUserName=${dockerUsername} \
                 -DdockerPackageName${dockerPackageName} \
                 -DdockerPort=${dockerPort} \
                 "set version in ThisBuild := \"$dockerTagName\"" \
                 dist/docker:publish
else
 echo "Missing dockerTagName param!"
fi
