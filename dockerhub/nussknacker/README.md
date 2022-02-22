# Nussknacker

This image contains [Nussknacker](http://nussknacker.io) distribution.

# Tags semantic

latest - always points to the latest, stable version

latest-staging - developer build with latest, not released yet features

# How to use

Follow steps described in [Quickstart](https://docs.nussknacker.io/quickstart/docker)

Repository with docker-compose presenting typical usage: [Nussknacker Quickstart](https://github.com/TouK/nussknacker-quickstart)

If you want to run image with embedded Streaming-Lite engine, just run:
```
docker run -it --network host -e DEFAULT_SCENARIO_TYPE=streaming-lite-embedded -e KAFKA_ADDRESS=localhost:3032 -e SCHEMA_REGISTRY_URL=http://localhost:3082 touk/nussknacker:latest
```
where:
- `-e DEFAULT_SCENARIO_TYPE=streaming-lite-embedded` - turning on embedded Streaming-Lite engine. By default, this variable is set to `streaming` (Streaming-Flink mode) which needs external dependencies.
- `--network host` - to be able to connect with kafka and schema registry exposed on host machine
- `-e KAFKA_ADDRESS=localhost:3032` - kafka address
- `-e SCHEMA_REGISTRY_URL=http://localhost:3082` - schema registry url

Both kafka and schema registry can be exposed e.g. using `docker-compose-env.yml` inside [Nussknacker Quickstart](https://github.com/TouK/nussknacker-quickstart)

After it started go to http://localhost:8080 and login using credentials: admin/admin.

# Configuration

Description about available environment variables and volumes is available on [Installation documentation](https://nussknacker.io/documentation/docs/installation_configuration_guide/Installation)

# License

Nussknacker is published under Apache License 2.0.