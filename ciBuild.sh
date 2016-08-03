#!/usr/bin/env bash

cd client
npm install
npm dist
cd -

cd server
./sbtwrapper clean test
