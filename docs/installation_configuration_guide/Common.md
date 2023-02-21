---
sidebar_position: 1
---
# Basics

The Docker image and the binary distribution contain minimal working [configuration file](https://github.com/TouK/nussknacker/blob/staging/nussknacker-dist/src/universal/conf/application.conf), which is designed as a base for further customizations using 
additional configuration files. 
This file is not used by the [Helm chart](https://artifacthub.io/packages/helm/touk/nussknacker), which prepares its own config file. 
## Configuration areas

Nussknacker configuration is divided into several configuration areas, each area addressing a specific aspect of using Nussknacker:

* [Designer](/about/GLOSSARY#nussknacker-designer) configuration (web application ports, security, various UI settings, database),
* Scenario Types configuration, comprising of:
  * [Deployment Manager](/about/GLOSSARY#deployment-manager) configuration, 
  * [Model](/about/GLOSSARY#model) configuration.

The Scenario Type is a convenient umbrella term for a particular Deployment Manager configuration and the associated model configuration. Diagram below presents main relationships between configuration areas.

![Configuration areas](img/configuration_areas.png "configuration areas")

Let's see how those concepts look in fragment of main configuration file:
```hocon
# Designer configuration 
environment: "local"
...

# Each scenario type is configured here 
scenarioTypes {
  "scenario-type-1": {
    # Configuration of DeploymentManager (Flink used as example here) 
    deploymentConfig: {
      type: "flinkStreaming"
      restUrl: "http://localhost:8081"
    }
    # Configuration of model
    modelConfig: {
      classPath: ["model/defaultModel.jar", "model/flinkExecutor.jar", "components/flink"]
      restartStrategy.default.strategy: disable
      components {
        ...
      }
    }
  }
}
```
It is worth noting that one Nussknacker Designer instance may be used to work with multiple Scenario Types which:

* can be deployed with various Deployment Managers to e.g. different Flink clusters
* use different components and Model configurations 

See [development configuration](https://github.com/TouK/nussknacker/blob/staging/nussknacker-dist/src/universal/conf/dev-application.conf#L33) (used to test various Nussknacker features) for an example of configuration with more than one Scenario Type.                   

## Environment variables

Environment variables are described in [Installation guide](./Installation.md), they are mostly helpful in the docker setup.

## Conventions

* We use HoCon (see [introduction](https://github.com/lightbend/config#using-hocon-the-json-superset) or [full specification](https://github.com/lightbend/config/blob/master/HOCON.md) for details) as our main configuration format. [Lightbend config library](https://github.com/lightbend/config/tree/master) is used for parsing configuration files - you can check [documentation](https://github.com/lightbend/config#standard-behavior) for details on conventions of file names and merging of configuration files.
* `nussknacker.config.locations` Java system property (`CONFIG_FILE` environment variable for Docker image) defines location of configuration files (separated by comma). The files are read in order, entries from later files can override the former (using HoCon fallback mechanism) - see docker demo for example:
  * [setting multiple configuration files](https://github.com/TouK/nussknacker-quickstart/blob/main/docker/common/docker-compose.yml#L13)
  * [file with configuration override](https://github.com/TouK/nussknacker-quickstart/blob/main/docker/streaming/nussknacker/nussknacker.conf)
* If `config.override_with_env_vars` Java system property is set to true, it is possible to override settings with env variables. This property is set to true in the official Nussknacker Docker image.

It’s important to remember that model configuration is prepared a bit differently. Please read [model configuration](model/ModelConfiguration) for the details. 
