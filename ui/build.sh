#!/bin/sh
EXTRA_ARGS=$@

cd client
npm install
npm run build
cd -

rm -rf server/src/main/resources/web
mkdir -p server/src/main/resources/web
cp client/main.html server/src/main/resources/web
cp -r client/dist server/src/main/resources/web/static

cd server
./sbtwrapper $EXTRA_ARGS 'set test in assembly := {}' clean assembly
