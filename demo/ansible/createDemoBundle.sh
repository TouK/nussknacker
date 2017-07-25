#!/usr/bin/env bash
VERSION=$1
BUNDLE_DIR=/tmp/esp-bundle

rm -r $BUNDLE_DIR 2> /dev/null
mkdir $BUNDLE_DIR

#TODO: copy from global maven repo?
cp ../../ui/server/target/scala-2.11/esp-ui-assembly-$VERSION.jar $BUNDLE_DIR


#FIXME: replace nexus coordinates with maven central
if [[ "$VERSION" == *-SNAPSHOT ]]; then
   REPO=snapshots
else
   REPO=releases
fi
cd $BUNDLE_DIR && curl -O https://philanthropist.touk.pl/nexus/content/repositories/$REPO/pl/touk/esp/esp-management-sample_2.11/$VERSION/esp-management-sample_2.11-$VERSION-assembly.jar && cd -

#-C zeby zapakowac zawartosc katalogu
tar -cvzf /tmp/esp-bundle.tar.gz -C $BUNDLE_DIR .