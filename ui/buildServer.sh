#!/usr/bin/env bash

set -e

cd ..
./sbtwrapper assemblySamples
./sbtwrapper ui/assembly
cd -