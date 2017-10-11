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

git remote | grep github || git remote add github "https://$githubToken:x-oauth-basic@github.com/touk/nussknacker"
git fetch github
msg="`git log -1 --pretty=%B | head -n 1` - book update"
git checkout github/gh-pages -B gh-pages
cp -r _book/* ..
git add ..
git commit -m "$msg"
git push github gh-pages
