Usage
=====
* Env variable CODE_LOCATION has to point to jar with model, e.g. export CODE_LOCATION=../../management/sample/target/scala-2.11/esp-management-sample-assembly-0.1-SNAPSHOT.jar
* app/application.conf has to have entry class configured, e.g.   processConfigCreatorClass: "pl.touk.esp.engine.management.sample.DemoProcessConfigCreator"
* docker-compose up :)

TODO
====
* esp-ui-assembly-0.1-SNAPSHOT.jar has to be put manually in app/build
* esp database is not created automatically in influx 
* grafana dashboard is not added automatically, you have to import it from grafana folder
