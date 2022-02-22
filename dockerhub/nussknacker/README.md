# Nussknacker

This image contains [Nussknacker](http://nussknacker.io) distribution.

# Tags semantic

latest - always points to the latest, stable version

latest-staging - developer build with latest, not released yet features

# How to use

Follow steps described in [Quickstart](https://docs.nussknacker.io/quickstart/docker)

Repository with docker-compose presenting typical usage: [Nussknacker Quickstart](https://github.com/TouK/nussknacker-quickstart)

If you want to run image just to "take a look"  (without ability to deploy anything), run it with stubbed dependencies:

```
docker run -it -p 8080:8080 -e INFLUXDB_URL=http://localhost -e SCHEMA_REGISTRY_URL=http://dummy:8888 -e KAFKA_ADDRESS=dummy:9092 -e CONFIG_FORCE_scenarioTypes_streaming_deploymentConfig_type=stub  touk/nussknacker:latest
```
Try it out, default admin user credentials are admin/admin.

# Configuration

Description about available environment variables and volumes is available on [Installation documentation](https://nussknacker.io/documentation/docs/installation_configuration_guide/Installation)

# License

Nussknacker is published under Apache License 2.0.