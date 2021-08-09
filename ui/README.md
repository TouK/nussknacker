# Running UI with embedded model and stubbed DeploymentManager

You can run Nussknacker UI with your model in IDE via
helper class `LocalNussknackerWithSingleModel`. To use it, add `nussknacker-ui` module to
test classpath and prepare class similar to `RunGenericModelLocally`.
It can be run from e.g. Intellij without special configuration and it will run sample
Nussknacker UI config with your model.

# Running UI with full integration environment on docker

If you want to run Nussknacker UI with full integration environment (flink, kafka etc.) follow steps below

## Building required modules to run from shell/IDE

Before running either from console or from IDE you have to manually build:
- run `npm ci` in `ui/client` (only if you want to test/compile FE, see `Readme.md` in `ui/client` for more details)
- custom models (```assemblySamples``` in sbt - not needed if running from IDE with stubbed DeploymentManager, see below)
- DeploymentManager(s) (```assemblyDeploymentManagers``` in sbt - not needed if running from IDE with stubbed DeploymentManager, see below)
- UI (```ui/assembly``` in sbt, not needed if you want to use FE development mode)

## Running integration environment

- Clone https://github.com/TouK/nussknacker-quickstart 
- Run `docker-compose -f docker-compose-env.yml -f docker-compose-custom.yml up -d` inside it

## Running from command line

Run: `./runServer.sh`

## Running from IntelliJ:

Run existing configuration `NussknackerApp` automatically loaded from `./run/NussknackerApp.run.xml`

## Access to service
Service should be available at ~~http://localhost:8080/api~~

# Troubleshooting

1. If you want to build ui and have access to it from served application, you can execute:
```
sbt buildUi
```
It will produce static assets in `./ui/server/target/scala-XXX/classes/web/static/` that make them accessible via http://localhost:8080/

2. If you want to test verification mechanism, you need to make directory with savepoints available from your dev host. You can use `./bindSavepointsDirLocally.sh` script for that.
   At the end you need to turn `FLINK_SHOULD_VERIFY_BEFORE_DEPLOY` flag on in environment variables.
