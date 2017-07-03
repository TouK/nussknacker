#!/usr/bin/env bash
set -e
cd ..
./sbtwrapper ";clean;engineStandalone/assembly;management_sample/assembly"
SSH="sshpass -p t0ook ssh touk@poc-esp1"
$SSH "mkdir -p esp-engine-standalone"
sshpass -p t0ook scp engine-standalone/target/scala-2.11/esp-engine-standalone-assembly-0.1-SNAPSHOT.jar touk@poc-esp1:~/esp-engine-standalone
sshpass -p t0ook scp management/sample/target/scala-2.11/esp-management-sample-assembly-0.1-SNAPSHOT.jar touk@poc-esp1:~/esp-engine-standalone
sshpass -p t0ook scp scripts/application.conf touk@poc-esp1:~/esp-engine-standalone
sshpass -p t0ook scp scripts/restartEngine.sh touk@poc-esp1:~/esp-engine-standalone
$SSH "cd esp-engine-standalone && ./restartEngine.sh"