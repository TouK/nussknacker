#!/usr/bin/env bash
FILE=nussknacker-ui-assembly.jar
VERSION=$1

if [ ! -f ./${FILE} ]; then
    if [[ -z "${VERSION}" ]]; then
        echo "You have to either put ${FILE} in app/build OR build with 'version' build argument - put it in e.g docker-compose"
        exit 1
    fi
    echo "Using version ${VERSION} from repository"
    if [[ "$VERSION" == *-SNAPSHOT ]]; then
       REPO=https://oss.sonatype.org/content/repositories/snapshots
    else
       REPO=https://repo1.maven.org/maven2
    fi
    wget -O ${FILE} ${REPO}/pl/touk/nussknacker/nussknacker-ui_2.11/${VERSION}/nussknacker-ui_2.11-${VERSION}-assembly.jar
else
    echo "Using custom built ${FILE}"
fi