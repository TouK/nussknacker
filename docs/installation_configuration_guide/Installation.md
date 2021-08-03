# Installation and configuration guide

Nussknacker relies on several open source components like Flink or Kafka, 
which need to be installed together with Nussknacker. This document focuses on the configuration of Nussknacker and its integrations with those components; pls refer to Kafka, Flink, ... , 
documentation for details on how to configure them for optimal performance .
      
# Installation

## Docker based installation
                    
Nussknacker is available at [Docker hub](https://hub.docker.com/r/touk/nussknacker/). 
You can check an example usage with docker-compose at [Nussknacker Quickstart Repository](https://github.com/TouK/nussknacker-quickstart).

### Base Image

As a base image we uses `openjdk:11-jdk`. See [Open JDK's Docker hub](https://hub.docker.com/_/openjdk) for more details.

### Properties

Available Nussknacker image properties below.

Docker only:

| Property name  | Type     | Default value | Description
| -------------- | -------- | ------------- | -----------
| NUSSKNACKER_CONFIG_FILE | string | /opt/nussknacker/conf/application.conf | Location of application configuration. Can be used multiple comma separated list of files. They will be merged in order, via HOCON fallback mechanism
| NUSSKNACKER_LOG_FILE | string | /opt/nussknacker/conf/docker-logback.xml | Location of logging configuration
| NUSSKNACKER_APPLICATION_APP | string | pl.touk.nussknacker.ui.NussknackerApp | Name of class of Nussknacker application 
| DAEMON_USER | string | daemon |
| DAEMON_GROUP | string | daemon |

Properties available also in standalone setup: 

| Property name  | Type     | Default value | Description
| -------------- | -------- | ------------- | -----------
| STORAGE_DIR | string | ./storage | Location of HSQLDB database storage
| DB_URL | string | jdbc:hsqldb:file:${STORAGE_DIR}/db;sql.syntax_ora=true | Database URL 
| DB_DRIVER | string | org.hsqldb.jdbc.JDBCDriver | Database driver class name
| DB_USER | string | SA | User used for connection to database
| DB_PASSWORD | string | | Password used for connection to database
| DB_CONNECTION_TIMEOUT | int | 30000 | Connection to database timeout in millis
| AUTHENTICATION_METHOD | string | BasicAuth | Method of authentication. One of: BasicAuth, OAuth2
| AUTHENTICATION_USERS_FILE | string | ./conf/users.conf | Location of users configuration
| AUTHENTICATION_HEADERS_ACCEPT | string | application/json |
| OAUTH2_CLIENT_SECRET | string | |
| OAUTH2_CLIENT_ID | string | |
| OAUTH2_AUTHORIZE_URI | string | |
| OAUTH2_REDIRECT_URI | string | |
| OAUTH2_ACCESS_TOKEN_URI | string | |
| OAUTH2_PROFILE_URI | string | |
| OAUTH2_PROFILE_FORMAT | string | |
| OAUTH2_IMPLICIT_GRANT_ENABLED | boolean | |
| OAUTH2_ACCESS_TOKEN_IS_JWT | boolean | false |
| OAUTH2_USERINFO_FROM_ID_TOKEN | string | false |
| OAUTH2_JWT_AUTH_SERVER_PUBLIC_KEY | string | |
| OAUTH2_JWT_AUTH_SERVER_PUBLIC_KEY_FILE | string | |
| OAUTH2_JWT_AUTH_SERVER_CERTIFICATE | string | |
| OAUTH2_JWT_AUTH_SERVER_CERTIFICATE_FILE | string | |
| OAUTH2_JWT_ID_TOKEN_NONCE_VERIFICATION_REQUIRED | string | |
| OAUTH2_GRANT_TYPE | string | authorization_code |
| OAUTH2_RESPONSE_TYPE | string | code |
| OAUTH2_SCOPE | string | read:user |
| OAUTH2_AUDIENCE | string | |
| SIGNALS_TOPIC | string | nk.signals | Topic that can be used by custom components for sending signals to scenarios
| KAFKA_ADDRESS | string | kafka:9092 | Kafka address used by kafka components (sources, sinks) for messaging
| SCHEMA_REGISTRY_URL | string | http://schemaregistry:8081 | Address of Confluent Schema registry used for storing of data model
| MODEL_CLASS_PATH | list of strings | `["model/genericModel.jar"]` | Classpath of model (base components library)
| FLINK_ROCKSDB_CHECKPOINT_DATA_URI | string | | URL to Flink's rocksdb checkpoints - should be on some distributed filesystem visibled by all Flink TaskManagers 
| FLINK_REST_URL | string | http://jobmanager:8081 | URL to Flink's REST API - used for scenario's deployment
| FLINK_QUERYABLE_STATE_PROXY_URL | string | taskmanager:9069| URL to Flink's queryable state proxy service - can by used by custom components that exposes theirs state via queryable state API 
| GRAFANA_URL | string | /grafana | URL to Grafana. Is used on client (browser) site. Should be relative to Nussknacker URL to avoid CORS configuration need
| COUNTS_URL | string | http://influxdb:8086/query | URL to Influxdb used by counts mechanism

### File structure

| Location | Usage in configuration | Description
| -------- | -------------------- | -----------
| /opt/nussknacker/storage | Configured by STORAGE_DIR property | Location of HSQLDB database
| /opt/nussknacker/conf/application.conf | Configured by NUSSKNACKER_CONFIG_FILE property | Location of Nussknacker configuration. Can be overwritten or used next to other custom configuration. See NUSSKNACKER_CONFIG_FILE for details
| /opt/nussknacker/conf/docker-logback.xml | Configured by NUSSKNACKER_LOG_FILE property | Location of logging configuration. Can be overwritten to specify other logger logging levels
| /opt/nussknacker/conf/users.conf | Configured by AUTHENTICATION_USERS_FILE property | Location of Nussknacker Component Providers
| /opt/nussknacker/model/genericModel.jar | Used in MODEL_CLASS_PATH property | JAR with generic model (base components library)
| /opt/nussknacker/components | Can be used in MODEL_CLASS_PATH property | Directory with Nussknacker Component Provider JARS
| /opt/nussknacker/lib | | Directory with Nussknacker base libraries
| /opt/nussknacker/managers | | Directory with Nussknacker Deployment Managers
| /opt/flink/data/savepoints | Configured on Flink's side | Location that will be used for savepoints state verification. Should be shared between Flink's TaskManagers and Nussknacker

### Kubernetes - Helm chart

We provide [Helm chart](https://artifacthub.io/packages/helm/touk/nussknacker) with basic Nussknacker setup, including:
- Flink
- Kafka
- Grafana + InfluxDB
          
Please note that Flink, Kafka are installed in basic configuration - for serious production deployments you probably want to 
customize those to meet your needs. 

## Binary package installation
   
Released versions are available at [GitHub](https://github.com/TouK/nussknacker/releases)

### Prerequisites

We assume that `java` (recommended version is JDK 11) is on path. 
           
### Startup script
                     
We provide following scripts:
- `run.sh` - to run in foreground
- `run-daemonized.sh` - to run in background

### Logging

We use [Logback](http://logback.qos.ch/manual/configuration.html) for logging configuration. 
By default, the logs are placed in `${NUSSKNACKER_DIR}/logs`, with sensible rollback configuration.  
Please remember that these are logs of Nussknacker Designer, to see/configure logs of other compoenents (e.g. Flink)
please consult their documentation. 

### Systemd considerations
                                 
Nussknacker Designer can easily be run as systemd service. Below we give sample configuration:

```
[Unit]
Description=Nussknacker Designer

[Service]
Environment='STDOUT=/opt/nussknacker/logs/nussknacker/nussknacker.out'
Environment='JDK_JAVA_OPTIONS=-Dcom.sun.management.jmxremote.port=9999 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false'
ExecStart=/bin/sh -c "/opt/nussknacker/bin/run.sh >> ${STDOUT} 2>&1"
Restart=always
RestartSec=3
TimeoutStopSec=10
User=nussknacker

[Install]
WantedBy=default.target
```

## Additional components 

Nussknacker designer is not enough to be able to create and deploy scenarios. 
Please see diagram of components of typical Nussknacker deployment:

![Nussknacker components](./img/components.png "Nussknacker components")
                                                                   
The [quickstart](https://github.com/TouK/nussknacker-quickstart) contains `docker-compose` based 
sample installation of all needed components (and a few that are needed for the demo).

If you want to install them from the scratch or use already install at your organisation pay attention to:
- Metrics setup (please see quickstart for reference):
  - Configuration of metric reporter in Flink setup
  - Telegraf's configuration - some metric tags and names need to be cleaned  
  - Importing scenario dashboard to Grafana configuration
- Flink savepoint configuration. To be able to use scenario verification 
  (see `shouldVerifyBeforeDeploy` property in [Deployment manager documentation](./DeploymentManagerConfiguration.md)) 
  you have to make sure that savepoint location is available from Nussknacker designer (e.g. via NFS like in quickstart setup) 


# [Configuration](./Configuration.md)
