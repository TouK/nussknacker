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
           
### Startup script, variables

### Systemd considerations

### Logging

## Additional components 

Nussknacker designer is not enough to be able to create and deploy scenarios. 
Please see diagram of components of typical Nussknacker deployment:

![Nussknacker components](./img/components.png "Nussknacker components")
                                                                   
The [quickstart](https://github.com/TouK/nussknacker-quickstart) contains `docker-compose` based 
sample installation of all needed components (and a few that are needed for the demo).

If you want to install them from the scratch or use already install at your organisation pay attention to:
- Flink setup - 
- Telegraf setup
- Grafana setup



# [Configuration](./Configuration.md)