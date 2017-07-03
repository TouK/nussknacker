#!/usr/bin/env bash
set -e
cd ..
./sbtwrapper ";clean;engineStandalone/assembly;management_sample/assembly"
mkdir -p /tmp/esp-engine-standalone
cp engine-standalone/target/scala-2.11/esp-engine-standalone-assembly-0.1-SNAPSHOT.jar /tmp/esp-engine-standalone/
cp management/sample/target/scala-2.11/esp-management-sample-assembly-0.1-SNAPSHOT.jar /tmp/esp-engine-standalone/
cp scripts/application.conf /tmp/esp-engine-standalone/
cd /tmp/esp-engine-standalone
java -Dconfig.file=application.conf -jar esp-engine-standalone-assembly-0.1-SNAPSHOT.jar 8090 8091