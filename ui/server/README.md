# Running from IntelliJ:
1. Find class 'pl.touk.nussknacker.ui.NussknackerApp'
2. Edit run [configuration](https://www.jetbrains.com/help/idea/run-debug-configurations.html)

    * Main class:         pl.touk.nussknacker.ui.NussknackerApp
    * VM options:         -Dconfig.file=./develConf/sample/application.conf -Dlogback.configurationFile=./logback-dev.xm
    * Working directory:  should be set to ui/server
    * Module classpath:   ui 

# Running backend for frontend development
If you want run backend only for front-end development, please run `./runServer.sh`

# Running full env (for integration tests)
* Go to docker/demo and run `docker-compose -f docker-compose-env.yml up -d` //runs full env with kafka / flink / etc..
* Run nussknacker by IntelliJ or `./runServer.sh`
 
# Access to service
 Service should be available at http://localhsot:8080/api