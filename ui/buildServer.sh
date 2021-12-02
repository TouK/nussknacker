#!/usr/bin/env bash

set -e

cd ..
./sbtwrapper "set ThisBuild / packageDoc / publishArtifact := false; set Compile / doc / sources := Seq.empty; dist/Universal/stage"
cd -
