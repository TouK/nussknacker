#!/usr/bin/env bash

set -e

cd "$(dirname $0)"
cd client && npm ci && npm run build && cd -
cp -r client/.federated-types/nussknackerUi submodules/types/@remote
cd submodules && npm ci && CI=true npm run build && cd -