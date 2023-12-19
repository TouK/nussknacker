#!/usr/bin/env bash

set -e

cd "$(dirname -- "$0")"
echo "Using Node version $(node -v)"
cd client && npm ci && npm run build && cd -
cp -r client/.federated-types/nussknackerUi submodules/types/@remote
cd submodules && npm ci && CI=true npm run build && cd -
# Copy designer dist for purpose of further usage in server ran from Idea
cd ..
sbt designer/copyClientDist
