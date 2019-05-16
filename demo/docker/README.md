This is part of Nussknacker's [quickstart](https://touk.github.io/nussknacker/Quickstart.html).

Images
=====
Docker images is build on each commit. Available nusskancker images you can find at: https://hub.docker.com/r/touk/nussknacker/tags. 
Default image tag is: demo-latest.  

Demo version
=======
Demo version available at: https://demo.nussknacker.io. Access data: admin / admin. 

Running
=======
* Env variable NUSSKNACKER_VERSION has to point to docker tag with model.
  You can set it in .env file. 
    * Sample file (.env) is provided. It assumes that jar with model is located at docker image in /opt/nussknacker/model/exampleModel.jar.
    * ./downloadSampleAssembly.sh ([version]) script is also provided. It can build sample model or downloaded released version. 
    * You can change model by mount downloaded version in docker-composer.yml  
* `docker-compose up -d` or `docker-compose -f docker-compose-file.yml up -d`:)
