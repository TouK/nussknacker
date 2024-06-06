#!/bin/bash -ex

if [ -z "$ACCESS_TOKEN" ]; then
    echo "ACCESS_TOKEN variable has to be defined"
    exit 1
fi

rm -rf nu-installation-example-repo
git clone "https://$ACCESS_TOKEN@github.com/TouK/nussknacker-installation-example.git" nu-installation-example-repo

# Copy the folder to the cloned repository
rm -rf nu-installation-example-repo/*
cp -r examples/installation/* nu-installation-example-repo/
git remote set-url origin "https://$ACCESS_TOKEN@github.com/TouK/nussknacker-installation-example.git"

cd nu-installation-example-repo
git config user.email "actions@github.com"
git config user.name "GitHub Actions"
git add .
# todo: version in the commit message
git commit -m "Push $FOLDER_TO_PUSH from source repository"
git push -f origin master
# todo: tag
