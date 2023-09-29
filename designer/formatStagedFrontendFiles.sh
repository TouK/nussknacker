#!/usr/bin/env sh

echo "Formatting frontend files ..."

npm run commit --if-present --prefix designer/client
npm run commit --if-present --prefix designer/submodules