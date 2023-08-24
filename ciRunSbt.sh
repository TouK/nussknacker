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
# Tuning of sbt test to prevent travis OOM from killing java. Be aware that using JAVA_OPTS won't work on travis because
# it has own alias for sbt: https://www.scala-sbt.org/1.x/docs/Travis-CI-with-sbt.html#Custom+JVM+options
# TODO: fix classloader leak during tests - for now, we increase max metaspace size.
echo "Executing: sbt -J-Xss6M -J-Xms2g -J-Xmx2g -J-XX:ReservedCodeCacheSize=256M -J-XX:MaxMetaspaceSize=5g $ARGS"
sbt -J-Xss6M -J-Xms2g -J-Xmx2g -J-XX:ReservedCodeCacheSize=256M -J-XX:MaxMetaspaceSize=5g $ARGS
