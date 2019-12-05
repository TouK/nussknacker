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
# tuning of sbt test to prevent travis OOM from killing java
SBT_OPTS="-J-Xmx2G -J-XX:ReservedCodeCacheSize=256M -J-Xss6M -J-XX:+UseConcMarkSweepGC -J-XX:+CMSClassUnloadingEnabled"
echo "Executing: sbt $SBT_OPTS $ARGS"
sbt $SBT_OPTS $ARGS
