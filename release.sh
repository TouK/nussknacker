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

cd ui/client && npm ci && cd -

# Copy-paste from ./ciRunSbt.sh with slight difference that args are passed in one string - see https://stackoverflow.com/a/3816747
ARGS="release $@"
JAVA_OPTS_VAL="-Xmx2G -XX:ReservedCodeCacheSize=256M -Xss6M -XX:+UseConcMarkSweepGC -XX:+CMSClassUnloadingEnabled"
echo "Executing: JAVA_OPTS=\"$JAVA_OPTS_VAL\" sbt \"$ARGS\""
JAVA_OPTS="$JAVA_OPTS_VAL" ./sbtwrapper -DdockerUpLatest=${dockerUpdateLatest} "$ARGS"

if [[ "$BACKPORT" == "true" ]]; then
  echo "Backport release - Skipping updating master"
elif [[ "$RC" == "true" ]]; then
  echo "Release Candidate - Skipping updating master"
else
  echo "Normal release - Updating master"
  git push origin HEAD:master -f 
fi
