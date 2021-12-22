# Contributing

## How to get involved?

All forms of contribution are welcome, including:
- Bug reports, proposals: https://github.com/TouK/nussknacker/issues/new/choose
- Pull requests: https://github.com/TouK/nussknacker/pulls

## Releasing strategy

We are trying to have regular releases (about once every two months), similar to [Kafka way](https://cwiki.apache.org/confluence/display/KAFKA/Time+Based+Release+Plan).

Our version has schema `epoch.major.patch` which means that changes on both first and second part can be not backward compatible.

## Branches conventions

We have currently some conventions for branches:
- `staging` - branch with current, cutting edge changes - is is tested only via our CI (manual tests are performed only before release), can have some "non final" features
- `release/X.Y` - release branch that is cut-off a few weeks before a release (see [Releasing strategy](##Releasing strategy) for details) and holds backports for patch releases
- `preview/xxx` - preview branches - pushing to them will cause publication of snapshots on sonatype and docker images on docker hub
- `master` - has latest, released version

## Working with Pull requests

If you want to discuss some changes before reporting it, feel free to submit [Discussion](https://github.com/TouK/nussknacker/discussions/new?category=q-a) or to ask via [mailing list](https://groups.google.com/forum/#!forum/nussknacker).

If you already prepared a change just submit [Pull request](https://github.com/TouK/nussknacker/compare) using target `staging` branch and someone should review the change.

Please add changes after review using new commits (don't ammend old once) to be able to see what was changed by reviewer.

After resolution of all commits and approval, pull request should be merged using "Squash and merge commit"

### Changelog

All significant changes should be added to [Changelog](docs/Changelog.md). All changes which break API compatibility
should be added to [Migration guide](docs/MigrationGuide.md)

### Documentation

New features, components should have appropriate [Documentation](docs). In particular, all settings
should be documented (with type, default values etc.) in appropriate sections of
[configuration guide](docs/installation_configuration_guide/Configuration.md).

## Working with code

### Setup

- JDK >= 9 is needed - we have specified target java version to java 8, but using some compiler flags available only on JDK >= 9 (--release flag)
- For building backend - standard `sbt` setup should be enough
- For building of frontend `node` and `npm` will be needed - see [client README](ui/client/README.md) for detailed instruction
- Some tests requires `docker`

### Running

#### Running Designer with embedded model and stubbed DeploymentManager

You can run Nussknacker UI with your model in IDE via
helper class `LocalNussknackerWithSingleModel`. To use it, add `nussknacker-ui` module to
test classpath and prepare class similar to `RunDefaultModelLocally`.
It can be run from e.g. Intellij without special configuration and it will run sample
Nussknacker UI config with your model.

#### Running full version of Designer from IntelliJ

Before running from IDE you have to manually build:
- run `npm ci && npm run build` in `ui/client` (only if you want to test/compile FE, see `Readme.md` in `ui/client` for more details)
- run `prepareDev` in sbt - it prepares components, models and copies FE files (generated above)

Run existing configuration `NussknackerApp` automatically loaded from `./run/NussknackerApp.run.xml`

#### Running full version of Designer from command line

Building:
- run `npm ci && npm run build` in `ui/client` (only if you want to test/compile FE, see `Readme.md` in `ui/client` for more details)
- run `./buildServer.sh` in `ui`

Run `./runServer.sh` in `ui`

#### Running using integration environment

- Clone [nussknacker-quickstart](https://github.com/TouK/nussknacker-quickstart)
- Run `docker-compose -f docker-compose-env.yml -f docker-compose-custom.yml up -d` inside it

#### Accessing service

Service should be available at ~~http://localhost:8080/api~~

#### Troubleshooting

1. If you want to build ui and have access to it from served application, you can execute:
```
cd ui/client
npm ci
npm run build
cd ../..
sbt copyUiDist
```
It will produce static assets and copy them to `./ui/server/target/scala-XXX/classes/web/static/` that make them accessible via http://localhost:8080/

```
cd ui
cp -r client/.federated-types/nussknackerUi submodules/types/@remote
cd submodules
npm ci
CI=true npm run build
cd ../..
sbt copyUiSubmodulesDist
```
It will produce submodules static assets and copy them to `./ui/server/target/scala-XXX/classes/web/submodules/` that make them accessible via http://localhost:8080/submodules/*

2. If you want to test the verification mechanism (used during redeployment of Flink scenarios), you need to make a directory with savepoints available from your dev host. You can use `./bindSavepointsDirLocally.sh` script for that.
   At the end you need to turn `FLINK_SHOULD_VERIFY_BEFORE_DEPLOY` flag on in environment variables.

### Building

#### Building tarball
```sbt dist/Universal/packageZipTarball```

#### Building stage - stage is a local directory with all the files laid out as they would be in the final distribution
```sbt dist/Universal/stage```

#### Publish jars to local repository
```sbt dist/Universal/publishLocal```

### Automated testing

You can run tests via `sbt test` or using your IDE.

#### Kubernetes tests

K8s tests are not run by default using `sbt test`. You need to run tests in `ExternalDepsTests` scope e.g. `sbt liteK8sDeploymentManager/ExternalDepsTests/test`.
They will use local `KUBECONFIG` to connect to k8s cluster. If you want to setup your local one, we recommend usage of [k3d](https://k3d.io/).
Tests use some images that should be available on your cluster. Importing of them is done automatically for `k3d`. 
For other clusters you can select other image tag using `dockerTagName` system environment variable or import image manually.  

### Code conventions

#### Java interoperability

The Nussknacker project is developed in Scala, but we want to keep API interoperable with Java in most of places.
Below you can find out some hints how to achieve that:
- Use abstract classes instead of traits in places where the main inheritance tree is easy to recognize
- Don't use package objects. If you need to have more classes in one file, prefer using files with name as a root class
  and subclasses next to root class or class with non-package object
- Be careful with abstract types - in some part of API will be better to use generics instead
- Be careful with `ClassTag`/`TypeTag` - should be always option to pass simple `Class[_]` or sth similar
- Prefer methods intead of function members (def foo: (T) => R)
- Avoid using AnyVal if the class is in API that will be used from Java
