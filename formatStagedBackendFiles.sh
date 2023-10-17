#!/usr/bin/env sh
set -eu

echo "Formatting backend files..."

# use real sbt if possible: https://github.com/dwijnand/sbt-extras/issues/377
if hash sbt 2>/dev/null; then
  sbt formatStagedScalaFiles
else
  ./sbtwrapper formatStagedScalaFiles
fi
