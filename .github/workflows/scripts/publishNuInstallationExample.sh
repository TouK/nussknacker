#!/bin/bash -e

# Define folder to push
FOLDER_TO_PUSH="examples/installation/"

# Define target repository details
TARGET_OWNER="TouK"
TARGET_REPO="nussknacker-installation-example"
TARGET_BRANCH="master" # adjust as needed

# Clone the target repository
git clone https://github.com/$TARGET_OWNER/$TARGET_REPO.git temp_repo

# Copy the folder to the cloned repository
rm -rf temp_repo/*
cp -r $FOLDER_TO_PUSH temp_repo/

# Push the changes to the target repository
cd temp_repo
git config user.email "actions@github.com"
git config user.name "GitHub Actions"
git add .
git commit -m "Push $FOLDER_TO_PUSH from source repository"
git push -f origin $TARGET_BRANCH
