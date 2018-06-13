#!/usr/bin/env bash

#RELEASE_ID from https://api.github.com/repos/TouK/nussknacker/releases
RELEASE_ID=$1
GITHUB_TOKEN=$2
distDirectory='nussknacker-dist/target/universal'

./sbtwrapper ";clean;dist/universal:packageZipTarball"

distFile=`ls $distDirectory | grep 'tgz' | head -n 1`
distFilePath=$distDirectory/$distFile
uploadUrl="https://uploads.github.com/repos/TouK/nussknacker/releases/$RELEASE_ID/assets?name=$distFile"

echo uploading $distFilePath to release $RELEASE_ID
curl -v -XPOST \
    -H "Authorization: token $GITHUB_TOKEN" -H "Content-Type: application/x-compressed-tar" \
    --data-binary @$distFilePath \
    https://uploads.github.com/repos/TouK/nussknacker/releases/$RELEASE_ID/assets?name=$distFile
