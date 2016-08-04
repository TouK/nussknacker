#!/bin/sh

cd client
npm install
npm build
cd -

rm -rf server/src/main/resources/web
mkdir -p server/src/main/resources/web
cp client/index.html server/src/main/resources/web
cp -r client/dist server/src/main/resources/web/static

cd server
sbt clean assembly