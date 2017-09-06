#!/usr/bin/env bash
source $(dirname "$0")/.env
source $(dirname "$0")/bash/lib.sh

assertDirectory ${CODE_LOCATION}
if [ -z $1 ]; then
    VERSION=${NUSSKNACKER_VERSION}
else
    VERSION=$1
fi
echo "Downloading example model in version $VERSION"
if [[ "$VERSION" == *-SNAPSHOT ]]; then
    #TODO: what will be default snapshots location??
    REPO=https://oss.sonatype.org/content/repositories/snapshots
else
   REPO=https://repo1.maven.org/maven2
fi
wget -O ${CODE_LOCATION} ${REPO}/pl/touk/nussknacker/nussknacker-example_2.11/${VERSION}/nussknacker-example_2.11-${VERSION}-assembly.jar