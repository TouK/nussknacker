---
title: Basics
sidebar_position: 1
---
# Configuration

## Minimal configuration file

The Docker image and the binary distribution contain minimal working [configuration file](https://github.com/TouK/nussknacker/blob/staging/nussknacker-dist/src/universal/conf/application.conf), which is designed as a base for further customizations using 
additional configuration files. Check [Conventions section](#conventions) for more details how to amend and override the minimal configuration file. 
This file is not used by the [Helm chart](https://artifacthub.io/packages/helm/touk/nussknacker), which prepares its own config file. 

The location and name of the configuration files is defined by the `CONFIG_FILE` environment variable. Consult [Basic environment variables](../installation/Installation.md#basic-environment-variables) for information on how this variable is resolved. 

Details of K8s based configuration can be found in  [Nussknacker Helm chart documentation](https://artifacthub.io/packages/helm/touk/nussknacker). 
## Configuration areas

Nussknacker configuration is divided into several configuration areas, each area addressing a specific aspect of using Nussknacker:

* [Designer](/about/GLOSSARY#nussknacker-designer) configuration (web application ports, security, various UI settings, database),
* Scenario Types configuration, comprising of:
  * [Deployment Manager](/about/GLOSSARY#deployment-manager) configuration, 
  * [Model](/about/GLOSSARY#model) configuration.
  * [Category](/installation_configuration_guide/DesignerConfiguration/#scenario-type-categories) configuration

The Scenario Type is a convenient umbrella term for a particular Deployment Manager configuration and the associated model configuration. Diagram below presents main relationships between configuration areas.

![Configuration areas](img/configuration_areas.png "configuration areas")

### Configuration file

Let's see how those concepts look in fragment of the configuration file:

<pre>
{/* Somehow, everything which is in the "pre" block is treated as jsx by Docusaurus*/}
{/* so, we need to escape "{" */}
{/* and add leading spaces in a special way. If not jsx parser will remove them */}
{/* Finally, do not worry - this is a valid jsx comment - you will not see it on Nu page*/}

<b># Designer configuration </b> <br/>
environment: "local"  <br/> 
... <br/>
 <br/>
# Each scenario type is configured here  <br/>
scenarioTypes {"{"}  <br/>
{" "} "scenario-type-1": {"{"}<br/>
{" "}   # Configuration of DeploymentManager (Flink used as example here)  <br/>
{" "}   <b>deploymentConfig:</b> {"{"} <br/>
{" "}       type: "flinkStreaming" <br/>
{" "}       restUrl: "http://localhost:8081" <br/> 
{" "}       } <br/>
{" "}   # Configuration of model <br/>
{" "}   <b>modelConfig</b>: {"{"} <br/>
{" "}       classPath: ["model/defaultModel.jar", "model/flinkExecutor.jar", "components/flink"] <br/>
{" "}       restartStrategy.default.strategy: disable <br/>
{" "}       components {"{"} <br/>
{" "}       ... <br/>
{" "}       } <br/>
{" "}    } <br/>
{" "}    category: "Default" <br/>
{" "}  } <br/>
} <br/>
</pre>

It is worth noting that one Nussknacker Designer instance may be used to work with multiple Scenario Types which:

* can be deployed with various Deployment Managers to e.g. different Flink clusters
* use different components and Model configurations 

See [development configuration](https://github.com/TouK/nussknacker/blob/staging/nussknacker-dist/src/universal/conf/dev-application.conf#L33) (used to test various Nussknacker features) for an example of configuration with more than one Scenario Type.                   

## Environment variables

Environment variables are described in the [Installation guide](../installation/Installation.md), they are mostly helpful in the docker setup.

## Conventions

* We use HOCON (see the [introduction](https://github.com/lightbend/config#using-hocon-the-json-superset) or the [full specification](https://github.com/lightbend/config/blob/master/HOCON.md) for details) as our main configuration format. [Lightbend config library](https://github.com/lightbend/config/tree/master) is used for parsing configuration files - you can check the [documentation](https://github.com/lightbend/config#standard-behavior) for details on conventions of file names and merging of configuration files.
* `nussknacker.config.locations` Java system property (`CONFIG_FILE` environment variable for Docker image) defines location of configuration files (separated by comma). The files are read in order, entries from later files can override the former (using HOCON fallback mechanism). This mechanism is used to extend or override default configuration contained in the [minimal configuration file](#minimal-configuration-file)  - see docker demo for example:
  * [setting multiple configuration files](https://github.com/TouK/nussknacker-quickstart/blob/main/docker/common/docker-compose.yml#L13)
  * [file with configuration override](https://github.com/TouK/nussknacker-quickstart/blob/main/docker/streaming/nussknacker/nussknacker.conf)
* If `config.override_with_env_vars` Java system property is set to true, it is possible to override settings with env variables. This property is set to true in the official Nussknacker Docker image.

It’s important to remember that model configuration is prepared a bit differently. Please read [model configuration](./model/ModelConfiguration.md) for the details. 

## What is next?
Most likely you will want to configure enrichers - they are configured under the `modelConfig.components` configuration key - see the [configuration file](#configuration-file). The details of enrichers configuration are in the [Integration chapter](../integration/) of the documentation. 
