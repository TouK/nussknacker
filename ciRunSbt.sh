#!/usr/bin/env bash
set -e
#if $CROSS_BUILD is true - we do cross build, otherwise we use default scala version
if [[ $CROSS_BUILD == 'true' ]]
then
   #for crossbuild we prepend each arg with +...
   ARGS=`echo " $*" | sed "s/ \+/ \+/g"`
else
   ARGS="$*"
fi
# Tuning of sbt test to prevent travis OOM from killing java. Be aware that using JAVA_OPTS won't work on travis because
# it has own alias for sbt: https://www.scala-sbt.org/1.x/docs/Travis-CI-with-sbt.html#Custom+JVM+options
# todo: fix classloader leak during tests - for now, we increase max metaspace size.
echo "Executing: sbt -J-Xss6M -J-Xms3g -J-Xmx3g -J-XX:ReservedCodeCacheSize=256M -J-XX:MaxMetaspaceSize=5g $ARGS"
sbt -J-Xss6M -J-Xms3g -J-Xmx3g -J-XX:ReservedCodeCacheSize=256M -J-XX:MaxMetaspaceSize=5g $ARGS
