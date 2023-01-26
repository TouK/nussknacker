#!/usr/bin/env bash

set -e

cd "$(dirname -- "$0")"/..
addDevArtifacts=true sbt "set ThisBuild / packageDoc / publishArtifact := false; set Compile / doc / sources := Seq.empty; prepareDev; dist/Universal/stage"
