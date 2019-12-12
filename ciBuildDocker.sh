#!/usr/bin/env bash

set -e

version=`echo ${APPLICATION_VERSION} | sed 's/[^a-zA-Z0-9-]/\_/g' | awk '{print tolower($0)}'`
dockerTagName=`echo ${DOCKER_TAG_NAME} | sed 's/[^a-zA-Z0-9-]/\_/g' | awk '{print tolower($0)}'`
dockerPackageName=${DOCKER_PACKAGENAME-"nussknacker"}
dockerUpdateLatest=${DOCKER_UPDATE_LATEST-"true"}
dockerUsername=${DOCKER_PACKAGE_USERNAME-"touk"}
dockerPort=${DOCKER_PORT-"8080"}
dockerPublishType=${DOCKER_PUBLISH_TYPE-"publish"}
addDevModel=${ADD_DEV_MODEL-"false"}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --version=*)
      version=`echo "${1#*=}" | sed 's/[^a-zA-Z0-9-]/\_/g' | awk '{print tolower($0)}'`
      ;;
    --docker-tag=*)
      dockerTagName=`echo "${1#*=}" | sed 's/[^a-zA-Z0-9-]/\_/g' | awk '{print tolower($0)}'`
      ;;
  --docker-port=*)
      dockerPort="${1#*=}"
      ;;
  --docker-user-name=*)
      dockerUsername="${1#*=}"
      ;;
  --docker-package-name=*)
      dockerPackageName="${1#*=}"
      ;;
  --docker-publish-type=*)
      dockerPublishType="${1#*=}"
      ;;
  --docker-update-latest=*)
      dockerUpdateLatest="${1#*=}"
      ;;
  --add-dev-model=*)
      addDevModel="${1#*=}"
      ;;
    *)
      printf " Error: Invalid argument: $1."
      exit 1
  esac
  shift
done

if [[ -z "$dockerTagName" ]]; then
    dockerTagName=${version}
fi

if [[ -n "$version" ]]; then
    cd ui/client && npm ci && cd -

    echo "Prepare docker build for version: $version, tag: $dockerTagName, port: $dockerPort," \
         "user: $dockerUsername, package: $dockerPackageName, update: $dockerUpdateLatest," \
         "publishType: $dockerPublishType."

    ./sbtwrapper -DdockerUserName=${dockerUsername} \
                 -DdockerPackageName${dockerPackageName} \
                 -DdockerPort=${dockerPort} \
                 -DdockerUpLatest=${dockerUpdateLatest} \
                 -DdockerTagName=${dockerTagName} \
                 -DaddDevModel=${addDevModel} \
                 "set version in ThisBuild := \"$version\"" \
                 dist/docker:"$dockerPublishType"
else
    echo "Missing version param!"
fi
