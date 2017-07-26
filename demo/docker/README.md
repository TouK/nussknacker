Building images
=====
* To build app image you can either:
  * put nussknacker-ui-assembly.jar in app/build folder
  * use released version (you can change it in docker-compose.yml file) 

Running
=======
* Env variable CODE_LOCATION has to point to jar with model.
  You can set it in .env file. 
    * Sample file (.env) is provided. It assumes that jar with model is located in /tmp/code-assembly.jar
    * getSampleAssembly.sh (./getSampleAssembly.sh ([version]) script is also provided. It can build sample model or downloaded released version.   
* app/conf/application.conf has to have entry class configured, e.g. processConfigCreatorClass: "pl.touk.nussknacker.engine.example.ExampleProcessConfigCreator"
* docker-compose up :)
