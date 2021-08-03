# Installation and configuration guide

Nussknacker relies on several open source components like Flink or Kafka, 
which need to be installed together with Nussknacker. This document focuses on the configuration of Nussknacker and its integrations with those components; pls refer to Kafka, Flink, ... , 
documentation for details on how to configure them for optimal performance .
      
# Installation

## Docker based installation
                    
Nussknacker is available at [Docker hub](https://hub.docker.com/r/touk/nussknacker/). 
To see how to install needed components 

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