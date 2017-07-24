#!/usr/bin/env bash
./sbtwrapper  -DnexusPassword=$1 clean test management/it:test publish
