# Nussknacker Lite runtime application

This image contains [Nussknacker](http://nussknacker.io) runtime for scenarios runned on Lite engine.

# Tags semantic

latest - always points to the latest, stable version

latest-staging - developer build with latest, not released yet features

# How to use

This image is not designed to be used from command line at first place. The main purpose of it is to be used programmatically by [Nussknacker K8s Deployment Manager](https://nussknacker.io/documentation/about/LiteArchitecture).

If you want to check it manually, the invocation will look like:
```
docker run -it --network host -e KAFKA_ADDRESS=localhost:3032 -e SCHEMA_REGISTRY_URL=http://localhost:3082 -v /tmp/scenario.json:/opt/nussknacker/conf/scenario.json -v /tmp/deploymentConfig.conf:/opt/nussknacker/conf/deploymentConfig.conf touk/nussknacker-lite-runtime-app:latest
```
where:
- /tmp/scenario.json - file with scenario json. You can get some by creating scenario in Streaming processing mode using Nussknacker and after that exporting it to file
- /tmp/deploymentConfig.conf - file with deployment configuration, currently it contains only `tasksCount` parameter with number of parallel tasks consuming topics. Example:
```
tasksCount: 2
```
- `--network host` - to be able to connect with kafka and schema registry exposed on host machine
- `-e KAFKA_ADDRESS=localhost:3032` - kafka address
- `-e SCHEMA_REGISTRY_URL=http://localhost:3082` - schema registry url

Both kafka and schema registry can be exposed e.g. using `docker-compose-env.yml` inside [Nussknacker Quickstart](https://github.com/TouK/nussknacker-quickstart)

# License

This image is published under Apache License 2.0.