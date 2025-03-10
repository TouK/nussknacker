#!/usr/bin/env sh
set -eu

echo "Checking backend files..."

# use real sbt if possible: https://github.com/dwijnand/sbt-extras/issues/377
if hash sbt 2>/dev/null; then
  sbt checkSqlSchemasInStagedScalaFiles formatStagedScalaFiles
else
  ./sbtwrapper checkSqlSchemasInStagedScalaFiles formatStagedScalaFiles
fi
