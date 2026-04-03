---
title: Install Nussknacker with Docker
description: Set up Nussknacker quickly using Docker. Use predefined images to run the Designer, Lite engine, and required services with minimal effort.
sidebar_label: Docker
---

Docker based installation

Nussknacker is available at [Docker Hub](https://hub.docker.com/r/touk/nussknacker/).
You can look at the example installation
with docker-compose at [installation example repository](https://github.com/TouK/nussknacker-installation-example/). 

:::note
The example above provides only a non-production ready way to run scenarios on Lite engine. For Lite engine production
setup, you will need a configured Kubernetes cluster. Using [Helm Installation](HelmChart.md) is recommended
in this case.
:::

You may want to see out [Quickstart in Docker](/docs/quickstart/dockerQuickstart.mdx) first too.

## Base Image

As a base image we use `eclipse-temurin:11-jre-jammy`. See [Eclipse Temurin Docker Hub](https://hub.docker.com/_/eclipse-temurin) for more
details.

## Container configuration

For basic usage, most things can be configured using environment variables. In other cases, can be mounted volume with
own configuration file. See [configuration](/docs/OSS_docs/configuration/Common.md) section for more details. 
`$NUSSKNACKER_DIR` is pointing to /opt/nussknacker.
