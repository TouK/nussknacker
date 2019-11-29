#!/usr/bin/env bash

profile=sample
if [ $# -ge 1 ]; then
  profile=$1
fi

cd ..
if [ "$profile" == "sample" ]; then
    ./sbtwrapper assemblySamples
fi
./sbtwrapper ui/assembly
cd -