#!/usr/bin/env bash
cd ./../..

./sbtwrapper "set test in assembly := {}" ui/assembly
cp ./ui/server/target/scala-2.11/esp-ui-assembly-0.1-SNAPSHOT.jar ./demo/docker/app/build/esp-ui-assembly.jar

./sbtwrapper "set test in assembly := {}" example/assembly

cd -
