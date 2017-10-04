#!/usr/bin/env bash

cd ..
./sbtwrapper management_sample/assembly
./sbtwrapper standalone_sample/assembly
./sbtwrapper ui/assembly
cd -