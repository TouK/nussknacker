#!/usr/bin/env bash

cd server
./sbtwrapper 'set test in assembly := {}' clean assembly
cd ..
./run.sh
