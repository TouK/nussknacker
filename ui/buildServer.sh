#!/usr/bin/env bash

set -e

cd ..
#assemblySamples is needed to use models, flinkProcessManager/assembly - to access Flink processes, ui/assembly - to be able to use FE
./sbtwrapper ;assemblySamples;flinkProcessManager/assembly;ui/assembly
cd -