#!/usr/bin/env sh

echo "Formatting frontend files ..."

npm run commit --if-present --prefix client
npm run commit --if-present --prefix submodules