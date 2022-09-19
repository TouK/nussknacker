#!/usr/bin/env bash

set -e
cd "$(dirname -- "$0")"

if [ -f ~/.sbt/1.0/docker.sh ]; then
  . ~/.sbt/1.0/docker.sh
fi

function publishRepoReadme {
  local repoName=$1
  local fullRepoName="touk/$repoName"

  echo "Updating README of repository: $fullRepoName"
  docker run -v "$PWD":/workspace \
    -e DOCKERHUB_USERNAME="$DOCKER_USERNAME" \
    -e DOCKERHUB_PASSWORD="$DOCKER_PASSWORD" \
    -e DOCKERHUB_REPOSITORY="$fullRepoName" \
    -e SHORT_DESCRIPTION="$(<"$repoName"/short.txt)" \
    -e README_FILEPATH="/workspace/$repoName/README.md" \
    peterevans/dockerhub-description:2 || echo "Update of $fullRepoName README failed"
}

publishRepoReadme "nussknacker"
publishRepoReadme "nussknacker-lite-runtime-app"
publishRepoReadme "nussknacker-request-response-app"
