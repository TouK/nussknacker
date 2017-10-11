#!/usr/bin/env bash
nexusPassword=$1
nexusHost=$2
githubToken=$3

set -e

currentHashCommit=`git rev-parse HEAD`
formattedDate=`date '+%Y-%m-%d-%H-%M'`
currentVersion=`cat version.sbt | grep "version in ThisBuild :=" | grep -Po '= "\K[^"]*'`
version=${formattedDate}-${currentHashCommit}-${currentVersion}

if [ -z "$nexusPassword" ]; then
    echo "nexusPassword missing"; exit -1
fi
if [ -z "$nexusHost" ]; then
    echo "nexusHost missing"; exit -1
fi

echo publishing nussknacker version: $version
./sbtwrapper clean test management/it:test
./sbtwrapper -DnexusPassword=${nexusPassword} -DnexusHost=${nexusHost} "set version in ThisBuild := \"$version\"" publish

if [[ ! -z $githubToken ]]; then
  # push to github mirror
  git remote | grep github || git remote add github "https://$githubToken:x-oauth-basic@github.com/touk/nussknacker"
  git fetch github
  git push github master
  
  # build & publish github doc
  [ -f node_modules/.bin/gitbook ] || npm install gitbook-cli
  PATH="$PATH:$(readlink -f node_modules/.bin)"
  cd docs
  ./publishToGithub.sh $githubToken
fi
