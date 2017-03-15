Uruchamianie z Idei:
====================
Main class:         pl.touk.esp.ui.EspUiApp
VM options:         -Dconfig.file=./server/develConf/application.conf -Dlogback.configurationFile=./logback.xml
Program arguments:  8081 ./server/develConf/jsons
Env variables:      includeFlinkAndScala=true (do ustawienia w File | Settings | Build, Execution, Deployment | Build Tools | SBT -> JVM Options -> VM Parameters)


#Uruchomienie backendu do developmentu
```./runServer.sh```

##przykladowe enpointy:

###lista procesow
```http://localhost:8081/api/processes```
