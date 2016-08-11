#!/bin/sh

cd client
npm install
npm run build
cd -

rm -rf server/src/main/resources/web
mkdir -p server/src/main/resources/web
cp client/index.html server/src/main/resources/web
cp -r client/dist server/src/main/resources/web/static

cd server
./sbtwrapper 'set test in assembly := {}' clean assembly

#run
#java -jar ./server/target/scala-2.11/esp-ui-assembly-0.1-SNAPSHOT.jar 8081