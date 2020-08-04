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
#JAVA_OPTS_VAL="-Xmx2G -XX:ReservedCodeCacheSize=256M -Xss6M -XX:+UseConcMarkSweepGC -XX:+CMSClassUnloadingEnabled"
#echo "Executing: JAVA_OPTS=\"$JAVA_OPTS_VAL\" sbt $ARGS"
sbt -J-Xss6M -J-Xms1500M -J-Xmx1500M -J-XX:ReservedCodeCacheSize=256M -J-XX:MaxMetaspaceSize=4G $ARGS
#JAVA_OPTS="$JAVA_OPTS_VAL" sbt $ARGS
