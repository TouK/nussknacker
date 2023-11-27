#!/usr/bin/env bash
set -e
# When $SCALA_VERSION is not present, we check for $CROSS_BUILD. If it's true - we do cross build, otherwise we use default scala version
if [[ -n "$SCALA_VERSION" ]]; then
   ARGS="++$SCALA_VERSION $*"
elif [[ $CROSS_BUILD == 'true' ]]; then
   #for crossbuild we prepend each arg with +...
   ARGS=`echo " $*" | sed "s/ \+/ \+/g"`
else
   ARGS="$*"
fi
echo "Executing: sbt $ARGS"
sbt $ARGS
