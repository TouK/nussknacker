# Configuration 

## Configuration areas

Nussknacker configuration is divided into several configuration areas, each area addressing a specific aspect of using Nussknacker:

* [Designer](about/GLOSSARY#nussknacker-designer) configuration
* Scenario Types configuration, comprising of:
    * [Deployment manager](/about/GLOSSARY#deployment-manager) / [Executor](/about/GLOSSARY#executor) configuration
    * [Model](/about/GLOSSARY#executor) configuration

Designer configuration  contains all settings for Nussknacker Designer - e.g. web application ports, security, various UI settings. 

One Nussknacker Designer deployment may be used to create various Scenario Types which:
                          
* can be deployed with various [Deployment Managers](DeploymentManagerConfiguration.md)  to e.g. different Flink clusters 
* Use different components and [Model configurations](ModelConfiguration.md) 

See [development configuration](https://github.com/TouK/nussknacker/blob/staging/nussknacker-dist/src/universal/conf/dev-application.conf#L33) (used to test various Nussknacker features) for an example of configuration with more than one Scenario Type.

Diagram below presents main relationships between configuration areas.

![Configuration areas](img/configuration_areas.png "configuration areas")


## Hocon - configuration format

We use HoCon (see [introduction](https://github.com/lightbend/config#using-hocon-the-json-superset) or [full specification](https://github.com/lightbend/config/blob/master/HOCON.md) for details) as our main configuration format. [Lightbend config library](https://github.com/lightbend/config/tree/master) is used for parsing configuration files - you can check [documentation](https://github.com/lightbend/config#standard-behavior) for details on conventions of file names and merging of configuration files. 

Following Nussknacker specific rules apply:

* `nussknacker.config.locations `system property (`CONFIG_FILE `environment variable for Docker image) defines location of configuration files (separated by comma). The files are read in order, entries from later files can override the former (using HoCon fallback mechanism) - see docker demo for example:
    * [setting multiple configuration files](https://github.com/TouK/nussknacker/blob/staging/demo/docker/docker-compose.yml#L12)
    * [file with configuration override](https://github.com/TouK/nussknacker/blob/staging/demo/docker/nussknacker/nussknacker.conf)
* [defaultUiConfig.conf](https://github.com/TouK/nussknacker/blob/staging/ui/server/src/main/resources/defaultUiConfig.conf) contains defaults for Nussknacker Designer
* `config.override_with_env_vars` is set to true, so it’s possible to override settings with env variables

It’s important to remember that Model configuration is prepared a bit differently. Please read [Model configuration](ModelConfiguration.md) for the details. 
