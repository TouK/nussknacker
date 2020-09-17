# Building required modules to run from shell/IDE

Before running either from console or from IDE you have to manually build:
- custom models (```assemblySamples``` in sbt)
- Flink ProcessManager (```flinkProcessManager/assembly``` in sbt, not needed if running from IDE, see below)
- UI (```ui/assembly``` in sbt, not needed if you want to use FE development mode)
You can do all steps at once with ```buildServer.sh``` script

# Running from IntelliJ:
1. Find class 'pl.touk.nussknacker.ui.NussknackerApp'
2. Edit run [configuration](https://www.jetbrains.com/help/idea/run-debug-configurations.html)

    * Main class:         pl.touk.nussknacker.ui.NussknackerApp
    * VM options:         -Dconfig.file=../../../nussknacker-dist/src/universal/conf/dev-application.conf -Dlogback.configurationFile=../logback-dev.xml
    * Working directory:  should be set to ui/server/work
    * Environment variables: 
AUTHENTICATION_USERS_FILE=../../../nussknacker-dist/src/universal/conf/users.conf;MANAGEMENT_MODEL_DIR=../../../engine/flink/management/sample/target/scala-2.12;GENERIC_MODEL_DIR=../../../engine/flink/generic/target/scala-2.12;DEMO_MODEL_DIR=../../../engine/demo/target/scala-2.12;STANDALONE_MODEL_DIR=../../../engine/standalone/engine/sample/target/scala-2.12
If you want to connect to infrastructure in docker you need to set on end of line also:
;FLINK_REST_URL=http://localhost:3031;FLINK_QUERYABLE_STATE_PROXY_URL=localhost:3063;SCHEMA_REGISTRY_URL=http://localhost:3082;KAFKA_ADDRESS=localhost:3032
    * Module classpath:   ui 
    * "Included dependencies with "Provided" scope" should be checked, so that Flink ProcessManager is included in the classpath

# Running backend for frontend development
If you want run backend only for front-end development, please run `./runServer.sh`

# Running full env (for integration tests)
* Go to docker/demo and run `docker-compose -f docker-compose-env.yml up -d` //runs full env with kafka / flink / etc..
* Run nussknacker by IntelliJ or `./runServer.sh`
 
# Access to service
 Service should be available at ~~http://localhost:8080/api~~
