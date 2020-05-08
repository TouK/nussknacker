#!/bin/sh

version=`cat version.sbt  | sed -e 's/[^"]*\"//' -e 's/\"//'`
[ "$(echo $version | grep -vc SNAPSHOT)" = 0 ] && echo "true" || echo "false"
