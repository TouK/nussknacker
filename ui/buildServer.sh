#!/usr/bin/env bash

set -e

cd ..
#assemblySamples is needed to use models, assemblyDeploymentManagers - to access process managers, ui/assembly - to be able to use FE
./sbtwrapper ";assemblySamples;assemblyDeploymentManagers;ui/assembly;assemblyComponents"
cd -
