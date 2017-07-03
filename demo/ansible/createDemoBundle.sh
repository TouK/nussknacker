#!/usr/bin/env bash
BUNDLE_DIR=/tmp/esp-bundle-0.1-SNAPSHOT

rm -r $BUNDLE_DIR 2> /dev/null
mkdir $BUNDLE_DIR

#TODO: pobieranie z nexusa?? czy jeszcze z innego miejsca??
cp ../../../ui/server/target/scala-2.11/esp-ui-assembly-0.1-SNAPSHOT.jar $BUNDLE_DIR

#lokalnie lub z nexusa, co kto lubi
#cp ~/.m2/repository/pl/touk/esp/esp-management-sample_2.11/0.1-SNAPSHOT/esp-management-sample_2.11-0.1-SNAPSHOT-assembly.jar $BUNDLE_DIR
cd $BUNDLE_DIR && curl -O http://nexus.touk.pl/nexus/content/repositories/snapshots/pl/touk/esp/esp-management-sample_2.11/0.1-SNAPSHOT/esp-management-sample_2.11-0.1-SNAPSHOT-assembly.jar && cd -

#-C zeby zapakowac zawartosc katalogu
tar -cvzf /tmp/esp-bundle-0.1-SNAPSHOT.tar.gz -C $BUNDLE_DIR .