#!/bin/bash -ex

if [ -z "$NU_INSTALLATION_EXAMPLE_ACCESS_TOKEN" ]; then
    echo "NU_INSTALLATION_EXAMPLE_ACCESS_TOKEN variable has to be defined"
    exit 1
fi

if [ -z "$NUSSKNACKER_VERSION" ]; then
    echo "NUSSKNACKER_VERSION variable has to be defined"
    exit 1
fi

cleanup() {
  rm -rf nu-installation-example-repo
}

cleanup # just for sure

trap cleanup EXIT

git clone "https://$NU_INSTALLATION_EXAMPLE_ACCESS_TOKEN@github.com/TouK/nussknacker-installation-example.git" nu-installation-example-repo
cd nu-installation-example-repo
git remote set-url origin "https://$NU_INSTALLATION_EXAMPLE_ACCESS_TOKEN@github.com/TouK/nussknacker-installation-example.git"

rm -rf ./*
cp -r ../examples/installation/* .
echo "NUSSKNACKER_VERSION=$NUSSKNACKER_VERSION" > .env

git config user.email "actions@github.com"
git config user.name "GitHub Actions"
git add .
git commit -m "Publishing $NUSSKNACKER_VERSION installation example"
git tag "$NUSSKNACKER_VERSION"
git push -f origin master --tags
