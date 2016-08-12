Uruchamianie fatjara:
=====================

`java  -Dconfig.file=develConf/application.conf -jar target/scala-2.11/esp-ui-assembly-0.1-SNAPSHOT.jar  8080 develConf/jsons`
gdzie:

- `-Dconfig.file` - lokalizacja pliku z konfiguracja
- `8080` - port na którym zostanie wystawione API
- `develConf/jsons` - lokalizacja katalogu z początkowymi procesami
