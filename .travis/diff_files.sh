#!/bin/sh

# based on: https://dev.to/ahferroin7/skip-ci-stages-in-travis-based-on-what-files-changed-3a4k

if [ -z "${TRAVIS_COMMIT_RANGE}" ] ; then
  CHANGED_FILES=;
else
  if [ "${TRAVIS_PULL_REQUEST}" = "false" ] ; then
    COMMIT_RANGE="$(echo ${TRAVIS_COMMIT_RANGE} | cut -d '.' -f 1,4 --output-delimiter '..')"
    CHANGED_FILES="$(git diff --name-only ${COMMIT_RANGE} --)"
  else
    CHANGED_FILES="$(git diff --name-only ${TRAVIS_BRANCH}..HEAD --)"
  fi
fi

FE_ONLY=$([ $(echo "$CHANGED_FILES" | grep -vc '^ui/client/.*$') == 0 ] && echo "true" || echo "false")
echo "$FE_ONLY"
