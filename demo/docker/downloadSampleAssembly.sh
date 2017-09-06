#!/usr/bin/env bash
if [ -z $1 ]; then
    source ./.env
    VERSION=${NUSSKNACKER_VERSION}
else
    VERSION=$1
fi
echo "Downloading example model in version $VERSION"
if [[ "$VERSION" == *-SNAPSHOT ]]; then
    #TODO: what will be default snapshots location??
   REPO=https://philanthropist.touk.pl/nexus/content/repositories/snapshots
else
   REPO=https://repo1.maven.org/maven2
fi
wget -O /tmp/code-assembly.jar ${REPO}/pl/touk/nussknacker/nussknacker-example_2.11/${VERSION}/nussknacker-example_2.11-${VERSION}-assembly.jar