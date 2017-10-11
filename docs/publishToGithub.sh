#!/usr/bin/env bash

githubToken=$1

if [[ -z $githubToken ]]; then
  echo "You need to pass github oauth token!"
  echo ""
  echo "Usage: $0 github_token"
  exit 1
fi

set -e

cd `dirname $0`

gitbook install
./buildDoc.sh

dir=/tmp/nussknacker_docs
msg="`git log -1 --pretty=%B | head -n 1` - book update"
rm -rf $dir
git clone --single-branch -b gh-pages "https://$githubToken:x-oauth-basic@github.com/touk/nussknacker" $dir
cp -r _book/* $dir
cd $dir
git add .
git commit -m "$msg"
git push origin gh-pages
