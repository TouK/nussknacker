Running from IntelliJ:
====================
1. find class 'pl.touk.nussknacker.ui.NussknackerApp'
1. run EspIuApp (should fail)
1. edit run [configuration](https://www.jetbrains.com/help/idea/run-debug-configurations.html)

    * Main class:         pl.touk.nussknacker.ui.NussknackerApp
    * VM options:         -Dconfig.file=./develConf/application.conf -Dlogback.configurationFile=./logback.xml
    * Program arguments:  8081 ./develConf/jsons
    * Working directory:  should be set to ui/server
    * Module classpath:   ui 

#Running backend for frontend development
```./runServer.sh```

##sample endpoints

###process list
```http://localhost:8081/api/processes```
