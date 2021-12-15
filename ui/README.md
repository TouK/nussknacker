# Running UI with embedded model and stubbed DeploymentManager

You can run Nussknacker UI with your model in IDE via
helper class `LocalNussknackerWithSingleModel`. To use it, add `nussknacker-ui` module to
test classpath and prepare class similar to `RunDefaultModelLocally`.
It can be run from e.g. Intellij without special configuration and it will run sample
Nussknacker UI config with your model.

# Running UI with full integration environment on docker

If you want to run Nussknacker UI with full integration environment (flink, kafka etc.) follow steps below

## Building and running from IntelliJ
                                                 
Before running from IDE you have to manually build:
- run `npm ci && npm run build` in `ui/client` (only if you want to test/compile FE, see `Readme.md` in `ui/client` for more details)
- run `prepareDev` in sbt - it prepares components, models and copies FE files (generated above)

Run existing configuration `NussknackerApp` automatically loaded from `./run/NussknackerApp.run.xml`

## Building and running from command line

Building:
 - run `npm ci && npm run build` in `ui/client` (only if you want to test/compile FE, see `Readme.md` in `ui/client` for more details)
 - run `./buildServer.sh` in `ui`

Run `./runServer.sh` in `ui`

## Running integration environment

- Clone https://github.com/TouK/nussknacker-quickstart 
- Run `docker-compose -f docker-compose-env.yml -f docker-compose-custom.yml up -d` inside it

## Access to service
Service should be available at ~~http://localhost:8080/api~~

# Troubleshooting

1. If you want to build ui and have access to it from served application, you can execute:
```
cd ui/client
npm ci
npm run build
cd -
sbt copyUiDist
```
It will produce static assets and copy them to `./ui/server/target/scala-XXX/classes/web/static/` that make them accessible via http://localhost:8080/

```
cd ui
cp -r client/.federated-types/nussknackerUi submodules/types/@remote
cd ui/submodules
npm ci
CI=true npm run build
cd -
sbt copyUiSubmodulesDist
```
It will produce submodules static assets and copy them to `./ui/server/target/scala-XXX/classes/web/submodules/` that make them accessible via http://localhost:8080/submodules/*

2. If you want to test verification mechanism, you need to make directory with savepoints available from your dev host. You can use `./bindSavepointsDirLocally.sh` script for that.
   At the end you need to turn `FLINK_SHOULD_VERIFY_BEFORE_DEPLOY` flag on in environment variables.
