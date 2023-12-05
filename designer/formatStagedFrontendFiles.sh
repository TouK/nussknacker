#!/usr/bin/env sh
set -eu

echo "Formatting frontend files..."

npm run commit --if-present --prefix client
npm run commit --if-present --prefix submodules
