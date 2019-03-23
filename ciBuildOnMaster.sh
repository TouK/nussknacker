#!/usr/bin/env bash
nexusPassword=$1
nexusUrl=$2
githubToken=$3

set -e

currentHashCommit=`git rev-parse HEAD`
formattedDate=`date '+%Y-%m-%d-%H-%M'`
currentVersion=`cat version.sbt | grep "version in ThisBuild :=" | grep -Po '\K([^"]*)(?=-SNAPSHOT)'`
version=${formattedDate}-${currentHashCommit}-${currentVersion}

if [ -z "$nexusPassword" ]; then
    echo "nexusPassword missing"; exit -1
fi
if [ -z "$nexusUrl" ]; then
    echo "nexusUrl missing"; exit -1
fi

echo publishing nussknacker version: $version
./sbtwrapper clean test management/it:test engineStandalone/it:test ui/slow:test
./sbtwrapper -DnexusPassword=${nexusPassword} -DnexusUrl=${nexusUrl} ";set version in ThisBuild := \"$version\";set isSnapshot in ThisBuild := false" publish

if [[ ! -z $githubToken ]]; then
  # push to github mirror
  git remote | grep github || git remote add github "https://$githubToken:x-oauth-basic@github.com/touk/nussknacker"
  git fetch github
  git push github HEAD:master

  # build & publish github doc
  [ -f node_modules/.bin/gitbook ] || npm install gitbook-cli
  PATH="$PATH:$(readlink -f node_modules/.bin)"
  cd docs
  ./publishToGithub.sh $githubToken
fi
