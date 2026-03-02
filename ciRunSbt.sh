#!/usr/bin/env bash
set -e
ARGS=()
# When $NUSSKNACKER_SCALA_VERSION is not present, we check for $CROSS_BUILD. If it's true - we do cross build, otherwise we use default scala version
if [[ -n "$NUSSKNACKER_SCALA_VERSION" ]]; then
  # prepend each command chain with '++', 'set ...' resets this so we need to repeat '++' insertion
  scala_version_inserted=0
  for arg in "$@"; do
    if [[ "$arg" != set* ]] && (( !scala_version_inserted )); then
      ARGS+=("++$NUSSKNACKER_SCALA_VERSION")
      scala_version_inserted=1
    elif [[ "$arg" == set* ]]; then
      scala_version_inserted=0
    fi
    ARGS+=("$arg")
  done
elif [[ $CROSS_BUILD == 'true' ]]; then
  # for crossbuild we prepend each task with +...
  for arg in "$@"; do
    if [[ "$arg" == set* ]]; then
      ARGS+=("$arg")
    else
      ARGS+=("+$arg")
    fi
  done
else
  ARGS=("$@")
fi
echo "Executing: sbt $ARGS"
exec sbt "${ARGS[@]}"
