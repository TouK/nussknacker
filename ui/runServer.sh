#!/usr/bin/env bash

cd ..
./sbtwrapper -DincludeFlinkAndScala=true 'set test in assembly := {}' ui/assembly
cd -
./run.sh
