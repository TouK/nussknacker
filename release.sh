#!/usr/bin/env bash

set -e

if [[ "$BACKPORT" == "true" ]]; then
  echo "Backport release - 'latest' tag won't be updated on docker"
  dockerUpdateLatest="false"
elif [[ "$RC" == "true" ]]; then
  echo "Release Candidate - 'latest' tag won't be updated on docker"
  dockerUpdateLatest="false"
else
  echo "Normal release - 'latest' tag will be updated on docker"
  dockerUpdateLatest="true"
fi

# Login can't be done in release pipeline - see: https://github.com/sbt/sbt-native-packager/issues/654
if [ -f ~/.sbt/1.0/docker.sh ]; then
  . ~/.sbt/1.0/docker.sh
fi
echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin;

# Copy-paste from ./ciRunSbt.sh with slight difference that args are passed in one string - see https://stackoverflow.com/a/3816747
ARGS="release $@"
sbt -J-Xss6M -J-Xms2G -J-Xmx2G -J-XX:ReservedCodeCacheSize=256M -J-XX:MaxMetaspaceSize=2500M -DdockerUpLatest=${dockerUpdateLatest} "$ARGS"

if [[ "$BACKPORT" == "true" ]]; then
  echo "Backport release - Skipping update of master and dockerhub readme"
elif [[ "$RC" == "true" ]]; then
  echo "Release Candidate - Skipping update of master and dockerhub readme"
else
  echo "Normal release - Updating master and dockerhub readme"
  git push origin HEAD:master -f
  ./dockerhub/pulishReadme.sh
fi
