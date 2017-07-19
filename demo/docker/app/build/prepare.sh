#!/usr/bin/env bash
FILE=esp-ui-assembly.jar
VERSION=$1

if [ ! -f ./${FILE} ]; then
    if [[ -z "${VERSION}" ]]; then
        echo "You have to either put ${FILE} in app/build OR build with 'version' build argument - put it in e.g docker-compose"
        exit 1
    fi
    echo "Using version ${VERSION} from repository"
    #FIXME: replace nexus with maven central
    wget -O ${FILE} http://nexus.touk.pl/nexus/content/repositories/public/pl/touk/esp/esp-ui_2.11/${VERSION}/esp-ui_2.11-${VERSION}-assembly.jar
else
    echo "Using custom built ${FILE}"
fi