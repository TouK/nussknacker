Usage
=====
* TODO: remove it... Prepare app: ./prepare.sh
* Env variable CODE_LOCATION has to point to jar with model, e.g. export CODE_LOCATION=../../engine/example/target/scala-2.11/esp-example-assembly-0.1-SNAPSHOT.jar
  You can set it in .env file. Sample file (.env) is provided
* app/conf/application.conf has to have entry class configured, e.g.   processConfigCreatorClass: "pl.touk.esp.engine.example.ExampleProcessConfigCreator"
* docker-compose up :)

TODO
====
* esp-ui-assembly-0.1-SNAPSHOT.jar has to be put manually in app/build - should be downloaded from bintray or whatever...
