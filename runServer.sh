#!/usr/bin/env bash

cd server
./sbtwrapper 'set test in assembly := {}' clean assembly
./sbtwrapper test:compile #zeby zbudowac testJar
cd ..
./run.sh
