# Docker based installation

Nussknacker is available at [Docker Hub](https://hub.docker.com/r/touk/nussknacker/). You can check an example usage
with docker-compose at [Nussknacker Quickstart repository](https://github.com/TouK/nussknacker-quickstart) in `docker` directory.

Please note, that while you can install Designer with plain Docker (e.g. with `docker-compose`) with Lite engine configured, you still
need configured Kubernetes cluster to actually run scenarios in this mode - we recommend using Helm installation for that mode.

If you want to check locally Streaming processing mode with plain Docker and embedded engine just run:
```bash
docker run -it --network host -e KAFKA_ADDRESS=localhost:3032 -e SCHEMA_REGISTRY_URL=http://localhost:3082 touk/nussknacker:latest
```
Note: `--network host `only works on linux and is used to connect to existing kafka/schema registry. In case of other OS you have to use different methods to make it accessible from Nussknacker container (e.g start Kafka/SR and Nussknacker in a single docker network)
If you want to see Nussknacker in action without Kafka, using embedded Request-Response processing mode (scenario logic is exposed with REST API), run:
```bash
docker run -it -p 8080:8080 -p 8181:8181 touk/nussknacker:latest
```
After it started go to [http://localhost:8080](http://localhost:8080) and login using credentials: admin/admin.
REST endpoints of deployed scenarios will be exposed at `http://localhost:8181/scenario/<slug>`. Slug is defined in Properties, and by default it is scenario name.

More information you can find at [Docker Hub](https://hub.docker.com/r/touk/nussknacker/)

## Base Image

As a base image we use `eclipse-temurin:11-jre-jammy`. See [Eclipse Temurin Docker Hub](https://hub.docker.com/_/eclipse-temurin) for more
details.

## Container configuration

For basic usage, most things can be configured using environment variables. In other cases, can be mounted volume with
own configuration file. See [configuration](../configuration/Common.md#environment-variables) section for more details. NUSSKNACKER_DIR is pointing to /opt/nussknacker.
