#!/usr/bin/env bash
cd ./../..

#TODO: this can be now removed to use released versions :)
./sbtwrapper "set test in assembly := {}" ui/assembly
mkdir -p ./demo/docker/app/build/
cp ./ui/server/target/scala-2.11/esp-ui-assembly-0.1-SNAPSHOT.jar ./demo/docker/app/build/esp-ui-assembly.jar

./sbtwrapper "set test in assembly := {}" example/assembly

cd -
