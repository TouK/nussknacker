#!/bin/sh

set -e

# Login can't be done in release pipeline - see: https://github.com/sbt/sbt-native-packager/issues/654
if [ -f ~/.sbt/1.0/docker.sh ]; then
  . ~/.sbt/1.0/docker.sh
fi
echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin;

./ciRunSbt.sh "release $*"
