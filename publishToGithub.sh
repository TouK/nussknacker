#!/usr/bin/env bash
COMMIT=$1
if [[ -z $COMMIT ]]; then
  echo "You have to provide commit message..."
  exit 1
fi

gitbook build
DIR=/tmp/nussknacker_docs
rm -rf $DIR
git clone -b gh-pages git@github.com:TouK/nussknacker.git $DIR
cp -r _book/* $DIR
cd $DIR
git add .
git commit -m "${COMMIT}"
git push origin gh-pages



