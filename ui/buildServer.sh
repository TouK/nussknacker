#!/usr/bin/env bash

set -e

cd ..
#assemblySamples is needed to use models, assemblyEngines - to access process managers, ui/assembly - to be able to use FE
./sbtwrapper ;assemblySamples;assemblyEngines;ui/assembly
cd -