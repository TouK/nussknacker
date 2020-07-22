#!/bin/sh
set -e

echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin;
./ciBuildDocker.sh --version=${BUILD_VERSION} --docker-publish-type=publishLocal --add-dev-model=$ADD_DEV_MODEL
cd ./demo/docker && echo "NUSSKNACKER_VERSION=$BUILD_VERSION" > .env && ./testQuickstart.sh
docker push ${DOCKER_SOURCE_TAG}
docker tag ${DOCKER_SOURCE_TAG} ${DOCKER_REPOSITORY}:${SANITIZED_TRAVIS_BRANCH}-latest
docker push ${DOCKER_REPOSITORY}:${SANITIZED_TRAVIS_BRANCH}-latest
if [[ "$TRAVIS_BRANCH" == "master" ]]; then docker tag ${DOCKER_SOURCE_TAG} ${DOCKER_REPOSITORY}:demo-latest; fi
if [[ "$TRAVIS_BRANCH" == "master" ]]; then docker push ${DOCKER_REPOSITORY}:demo-latest; fi