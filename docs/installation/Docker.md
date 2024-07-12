# Docker based installation

Nussknacker is available at [Docker Hub](https://hub.docker.com/r/touk/nussknacker/).
You can check an example installation
with docker-compose at [installation example repository](https://github.com/TouK/nussknacker-installation-example/). 

Please note, in the example above the Lite engine is run inside the Designer. It's not a production setup and for production 
you will need a configured Kubernetes cluster to actually run scenarios on the Lite engine - we recommend using Helm installation for that mode.

You may want to see out [Quickstart in Docker](/quickstart/docker) first too.

## Base Image

As a base image we use `eclipse-temurin:11-jre-jammy`. See [Eclipse Temurin Docker Hub](https://hub.docker.com/_/eclipse-temurin) for more
details.

## Container configuration

For basic usage, most things can be configured using environment variables. In other cases, can be mounted volume with
own configuration file. See [configuration](../configuration/Common.md#environment-variables) section for more details. `$NUSSKNACKER_DIR` is pointing to /opt/nussknacker.
