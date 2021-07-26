# Configuration 


## Hocon - configuration format

We use HoCon (see [introduction](https://github.com/lightbend/config#using-hocon-the-json-superset) or [full specification](https://github.com/lightbend/config/blob/master/HOCON.md) for details) as our main configuration format. [Lightbend config library](https://github.com/lightbend/config/tree/master) is used for parsing configuration files - you can check [documentation](https://github.com/lightbend/config#standard-behavior) for details on conventions of file names and merging of configuration files. 

Following Nussknacker specific rules apply:

* `nussknacker.config.locations `system property (`NUSSKNACKER_CONFIG_FILE `environment variable for Docker image) defines location of configuration files (separated by comma). The files are read in order, entries from later files can override the former (using HoCon fallback mechanism) - see docker demo for example:
    * [setting multiple configuration files](https://github.com/TouK/nussknacker/blob/staging/demo/docker/docker-compose.yml#L12)
    * [file with configuration override](https://github.com/TouK/nussknacker/blob/staging/demo/docker/nussknacker/nussknacker.conf)
* [defaultUiConfig.conf](https://github.com/TouK/nussknacker/blob/staging/ui/server/src/main/resources/defaultUiConfig.conf) contains defaults for Nussknacker designer
* `config.override_with_env_vars` is set to true, so it’s possible to override settings with env variables

It’s important to remember that model configuration is prepared a bit differently. Please read [Model configuration](ModelConfiguration.md) for the details. 


## Configuration areas

Nussknacker configuration is divided into several configuration areas, each area addressing a specific aspect of using Nussknacker:

* Designer configuration
* Scenario types configuration
    * Deployment manager/executor configuration
    * Model configuration

[Designer configuration](DesignerConfiguration.md)  contains all settings for Nussknacker designer - e.g. web application ports, security, various UI settings. 

One Nussknacker designer deployment may be used to create various Scenario types which:
                          
* Deployed with various [deployment managers](DeploymentManagerConfiguration.md)  to e.g. different Flink clusters 
* Using different components and [model configurations](ModelConfiguration.md) 

See [development configuration](https://github.com/TouK/nussknacker/blob/staging/nussknacker-dist/src/universal/conf/dev-application.conf#L33) (used to test various Nussknacker features) for an example of configuration with more than one scenario type.

Diagram below presents main relationships between configuration areas.

![Configuration areas](img/configuration_areas.png "configuration areas")

