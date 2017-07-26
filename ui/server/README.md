Running from IntelliJ:
====================
Main class:         pl.touk.nussknacker.ui.EspUiApp
VM options:         -Dconfig.file=./develConf/application.conf -Dlogback.configurationFile=./logback.xml
Program arguments:  8081 ./develConf/jsons
Working directory:  should be set to ui/server
Module classpath:   ui          
Env variables:      includeFlinkAndScala=true (should be set in File | Settings | Build, Execution, Deployment | Build Tools | SBT -> JVM Options -> VM Parameters)


#Running backend for frontend development
```./runServer.sh```

##sample endpoints

###process list
```http://localhost:8081/api/processes```
