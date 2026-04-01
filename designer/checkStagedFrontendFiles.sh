#!/usr/bin/env sh
set -eu

if git diff --cached --name-only --diff-filter=ACM | grep --max-count=1 -E "^designer/client/" > /dev/null; then
  echo "Checking client files..."
  npm run commit --if-present --prefix client
fi

if git diff --cached --name-only --diff-filter=ACM | grep --max-count=1 -E "^designer/submodules/" > /dev/null; then
  echo "Checking submodules files..."
  npm run commit --if-present --prefix submodules
fi
