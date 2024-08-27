# Nussknacker

This image contains [Nussknacker](http://nussknacker.io) distribution.

# Tags semantic

latest - always points to the latest, stable version

latest-staging - developer build with latest, not released yet features

# Prerequisites
Docker in version 20.10.14+

# How to use

Repository with docker-compose presenting typical usage: [Nussknacker Quickstart](https://github.com/TouK/nussknacker-quickstart)

## Streaming | Lite engine 

If you want to run image with embedded Lite engine in Streaming processing mode (good for development, experiments, not suitable for production), just run:
```
docker run -it --network host -e KAFKA_ADDRESS=localhost:3032 -e SCHEMA_REGISTRY_URL=http://localhost:3082 touk/nussknacker:latest
```
where:
- `--network host` - to be able to connect with Kafka and Schema Registry exposed on host machine. Note: It only works on linux and is used to connect to existing kafka/schema registry. In case of other OS you have to use different methods to make it accessible from Nussknacker container (e.g start Kafka/SR and Nussknacker in a single docker network)
- `-e KAFKA_ADDRESS=localhost:3032` - Kafka address
- `-e SCHEMA_REGISTRY_URL=http://localhost:3082` - Schema Registry URL

Both Kafka and Schema Registry can be exposed e.g. using `docker-compose-env.yml` inside [Nussknacker Quickstart](https://github.com/TouK/nussknacker-quickstart/tree/old-quickstart)

After it started go to http://localhost:8080 and login using credentials: admin/admin.

## Request-response

To run image with embedded Request-response engine (good for development, experiments, not suitable for production), run:
```
docker run -it -p 8080:8080 -p 8181:8181 touk/nussknacker:latest
```
After it started go to http://localhost:8080 and login using credentials: admin/admin.  
REST endpoints of deployed scenarios will be exposed at `http://localhost:8181/scenario/<slug>`. Slug is defined in Properties, and by default it is scenario name.

# Configuration

Description about available environment variables and volumes is available on [Installation documentation](https://nussknacker.io/documentation/docs/installation/)

# License

Nussknacker is published under Apache License 2.0.
