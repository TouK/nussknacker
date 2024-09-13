# Contributing

## How to get involved?

All forms of contribution are welcome, including:
- Bug reports, proposals: https://github.com/TouK/nussknacker/issues/new/choose
- Pull requests: https://github.com/TouK/nussknacker/pulls

## Release strategy

We are trying to have regular releases (about once every two months), similar to [the way Kafka does it](https://cwiki.apache.org/confluence/display/KAFKA/Time+Based+Release+Plan).

Our version uses the schema `epoch.major.patch`, which means that changes on both first and second part may be not backward compatible.

## Branch naming conventions

We use the following conventions for branch names:
- `staging` - current, cutting edge changes - tested only via CI (manual tests are performed only before release), can have some "non-final" features
- `release/X.Y` - release branch that is cut-off a few weeks before a release (see [Release strategy](#release-strategy) for details), also holds backported patches
- `preview/xxx` - preview branches - pushing to them will cause publication of snapshots on Sonatype and Docker images on DockerHub
- `master` - the latest released version

## Working with Pull requests

If you want to discuss some changes before reporting them, feel free to [start a discussion](https://github.com/TouK/nussknacker/discussions/new?category=q-a) or ask via [mailing list](https://groups.google.com/forum/#!forum/nussknacker).

If you have already prepared a change just submit a [Pull request](https://github.com/TouK/nussknacker/compare) using `staging` branch as target and someone should review it.

Please add changes after a review by using new commits (don't amend old ones) to be able to easily see what was changed.

After resolution of all comments and approval, pull request should be merged using "Squash and merge commit"

### Changelog

All significant changes should be added to [Changelog](docs/Changelog.md). All changes which break API compatibility
should be added to [Migration guide](docs/MigrationGuide.md).

### Documentation

New features and components should have appropriate [Documentation](docs). In particular, all settings
should be documented (with type, default values etc.) in appropriate sections of
[Configuration documentation](docs/installation_configuration_guide/README.md).

When writing documentation please follow these instructions:
* all links to other sections in documentation (in this repo) should point to `md` or `mdx` files 
    
  example of correct link:
    ```
    [main configuration file](./Common.md#configuration-areas)
    ```
  example of incorrect link:
    ```
    [main configuration file](./Common#configuration-areas) 
    ```
* all links to `https://nussknacker.io/documentation/`, but other than `Documentation` section in that page, e.g. to `Quickstart` sections: 
  * should **not** point to `md` or `mdx` files
  * should **not** be relative
    
  example of correct link:
    ```
    [Components](/quickstart/GLOSSARY#component)
    ```
  example of incorrect links:
    ```
    [Components](/quickstart/GLOSSARY.md#component)
    [Components](../quickstart/GLOSSARY#component)
    ```
### Automatic Screenshots Updates in Documentation

> The screenshots from Nu GUI are taken automatically by Cypress test automation tool based on the test definition you need to create. Once recorded, during the "documentation test run" the test screenshot is compared against the stored screenshot. If they are different, a PR is created automatically.

#### To automate new screenshot in your documentation, follow these steps:

1. **Create a Cypress Test**: Begin by adding a new Cypress test in the `designer/client/cypress/e2e/autoScreenshotChangeDocs.cy.ts` file. In this test you have to choose a scenario form the `designer/client/cypress/fixtures/` folder (or create a new one). Then utilize one of the screenshot capture functions like `takeGraphScreenshot()`, `takeWindowScreenshot()` (or add a new one like those).

2. **Screenshot Storage** [nothing to do]: All captured screenshots are stored in the `docs/autoScreenshotChangeDocs` folder. These screenshots are named according to a specific convention:

    ``` 
    Auto Screenshot Change Docs - [name of test]#[index of image in test].png    
    ```

    For example:`Auto Screenshot Change Docs - basic_components - variable#0.png`

    Filenames assigned by Cypress are not accepted by Docusaurus - we modify them to meet Docusaurus naming requirements. The renaming takes place during upload of the documentation (`\docs` folder) to the repo from which documentation is served. For example, the screenshot file from the example above will be renamed to:

    ```
    Auto_Screenshot_Change_Docs_-_[name of test][index of image in test].png
    ```
    
    For example:`Auto_Screenshot_Change_Docs_-_basic_components_-_variable0.png`

3. **Use final screenshot name**: In the docs, make sure to reference the screenshots using the Docusaurus naming schema. For example:

    ```
    ![image](../autoScreenshotChangeDocs/Auto_Screenshot_Change_Docs_-_basic_components_-_variable0.png  "Scenario with variable declaration")
    ```
    
    By following these steps, you can easily update and reference screenshots in your documentation using an automated process.

> **Note**: Screenshots will not appear in you IDE `*.md` rendered preview. You can't do anything about it.
 
# Working with code

### Setup

- JDK >= 11 is needed
- For building backend - standard `sbt` setup should be enough
- For building of frontend `node` and `npm` will be needed - see [client README](designer/client/README.md) for detailed instruction
- Some tests require `docker`

### Running

#### Running Designer from IntelliJ

Before running from IDE you have to manually build:
- build fronted using [Building frontend instruction](#building-frontend) below (only if you want to test/compile FE, see `Readme.md` in `designer/client` for more details)
- run `sbt prepareDev` - it prepares components, models and copies FE files (generated above)

Run existing configuration `NussknackerApp` automatically loaded from `./run/NussknackerApp.run.xml`

#### Running full version of Designer from command line

Building:
- build fronted using [Building frontend instruction](#building-frontend) below
- run `./buildServer.sh` in `designer`

Run `./runServer.sh` in `designer`Documentation

Changing the version of the Scala is done by setting `NUSSKNACKER_SCALA_VERSION`, e.g. `NUSSKNACKER_SCALA_VERSION=2.12 ./buildServer.sh`

#### Running using integration environment

Use one of the following method:
1. run using SBT: `sbt designer/test:"runMain pl.touk.nussknacker.dev.RunEnvForLocalDesigner"`
2. run using Intellij configuration: `RunEnvForLocalDesigner` 
3. run Docker Compose: `docker compose -f examples/dev/local-testing.docker-compose.yml -f examples/dev/nu-scala213.override.yml up -d`

You can also customize the setup by adding your changes in separate yaml file:
* like this: `sbt designer/test:"runMain pl.touk.nussknacker.dev.RunEnvForLocalDesigner --customizeYaml=/tmp/my.override.yml"`
* or this: `docker compose -f examples/dev/local-testing.docker-compose.yml -f examples/dev/nu-scala213.override.yml -f /tmp/my.override.yml up -d`

By default, an environment for Scala 2.13 is prepared. To run one for Scala 2.12:
* run: `sbt designer/test:"runMain pl.touk.nussknacker.dev.RunEnvForLocalDesigner --scalaV scala212"`
* or run: `docker compose -f examples/dev/local-testing.docker-compose.yml up -d` 

#### Running Designer with model classes on the same classes as designer

To shorten loopback loop during testing of your locally developed components, you can run Nussknacker UI 
in IDE with model classes on the same classpath. Thanks to that, you can skip (re)building of components jars stage.
To test flink-streaming components just run `RunFlinkStreamingModelLocally` from your IDE.
Be aware that it uses stubbed version of DeploymentManager so it won't be possible to deploy scenarios.

If you want to test other components, just extends helper class `LocalNussknackerWithSingleModel`
and add dependency to `designer` module like in flink-streaming case.

#### Setting up Kubernetes environment

To run streaming Lite scenarios with K8s, we recommend using [k3d](https://k3d.io) with
[nussknacker-quickstart](https://github.com/TouK/nussknacker-quickstart/tree/old-quickstart) setup
- run integration environment, as described above
- `K3D_FIX_DNS=1 PROJECT_ROOT=$(pwd) k3d cluster create --network nussknacker_network --config=.k3d/single-cluster.yml` 
  This will create K8s cluster, which has access to Docker network used by integration environment. [K3D_FIX_DNS](https://github.com/rancher/k3d/issues/209).
  This cluster will use nginx ingress controller instead of default Traefik to make `rewrite-target` annotation works correctly
- run `sbt buildAndImportRuntimeImageToK3d` (can be skipped if you intend to use e.g. `staging-latest` Docker image)

#### Accessing service

Service should be available at `http://localhost:8080/api`. Swagger UI with the OpenAPI document of the API will
be available at `http://localhost:8080/api/docs`.

#### Troubleshooting

1. If you want to build frontend and have access to it from served application, you can build it using [Building frontend instruction](#building-frontend) below.
It will at the end:
- copy main application static files to `./designer/server/target/scala-XXX/classes/web/static/` and make them accessible via http://localhost:8080/
- copy submodules static files to `./designer/server/target/scala-XXX/classes/web/submodules/` and make them accessible via http://localhost:8080/submodules/*

2. If you want to test the verification mechanism (used during redeployment of Flink scenarios), you need to make a directory with savepoints available from your dev host. You can use `./bindSavepointsDirLocally.sh` script for that.
   At the end you need to turn `FLINK_SHOULD_VERIFY_BEFORE_DEPLOY` flag on in environment variables.

### Building

#### Building frontend
```
./designer/buildClient.sh
```
For more details see [client README](designer/client/README.md)

#### Building tarball
```sbt dist/Universal/packageZipTarball```

#### Building stage - stage is a local directory with all the files laid out as they would be in the final distribution
```
sbt dist/Universal/stage
```

#### Publish Docker images to local repository
```
sbt dist/Docker/publishLocal
```

#### Publish jars to local maven repository
```sbt publishM2```

#### Generating Nu Designer API OpenAPI static document
```sbt generateDesignerOpenApi```

### Automated testing

You can run tests via `sbt test` or using your IDE.

#### Kubernetes tests

K8s tests are not run by default using `sbt test`. You need to run tests in `ExternalDepsTests` scope e.g. `sbt liteK8sDeploymentManager/ExternalDepsTests/test`.
They will use local `KUBECONFIG` to connect to K8s cluster. If you want to set up your local one, we recommend usage of [k3d](https://k3d.io/).
Tests use some images that should be available on your cluster. Importing of them is done automatically for `k3d`. 
For other clusters you can select other image tag using `dockerTagName` system environment variable or import image manually.  

#### Azure integration tests

Azure integration tests are not run by default using `sbt test`. You need to run tests in `ExternalDepsTests` scope e.g. `sbt schemedKafkaComponentsUtils/ExternalDepsTests/test`.
To run them you should have configured one of authentication options described here:
https://github.com/Azure/azure-sdk-for-java/wiki/Azure-Identity-Examples#authenticating-with-defaultazurecredential e.g. Intellij plugin, Azure CLI or environment variables.
To run the tests set up environment variables:
- AZURE_EVENT_HUBS_NAMESPACE, that is Event Hubs Namespace where schema registry is registered (by default nu-cloud).
- AZURE_EVENT_HUBS_SHARED_ACCESS_KEY_NAME and AZURE_EVENT_HUBS_SHARED_ACCESS_KEY, to configure Kafka admin client that uses "sasl.jaas.config" to authenticate
(see properties configuration in https://nussknacker.io/documentation/cloud/azure/#setting-up-nussknacker-cloud)

### Using logs in tests

We have a dedicated module with test extensions (`testUtils`) which contains `logback-test.xml` with things like:
- Some loggers that are too verbose on INFO level, have decreased level (e.g. in kafka, zk)
- Some loggers that are too silent on INFO level, have increased level (e.g. in flink)
- Some loggers use environment variables to determine level e.g. NUSSKNACKER_LOG_LEVEL. You can leverage configurations
  templates in Idea to globally set level to the desired level for all tests runned from Idea.

Default levels are prepared for work with CI - they are not too verbose to not hit the limit of logs, but for local use
it is convenient to increase levels by environment variables.
Please don't add a custom `logback-test.xml` to any module. Instead, reuse the existing configuration from `testUtils`.

### Code conventions

#### Java interoperability

The Nussknacker project is developed in Scala, but we want to keep API interoperable with Java in the most places.
Below you can find out some hints how to achieve that:
- Use abstract classes instead of traits in places where the main inheritance tree is easy to recognize
- Don't use package objects. If you need to have more classes in one file, prefer using files with name as a root class
  and subclasses next to root class or class with non-package object
- Be careful with abstract types - in some part of API will be better to use generics instead
- Be careful with `ClassTag`/`TypeTag` - should be always option to pass simple `Class[_]` or sth similar
- Prefer to use methods instead of member functions (def foo: (T) => R)
- Avoid using AnyVal if the class is in API that will be used from Java
- Prefer adding the `final` keyword to `case classes` definitions if you have no serious reason to skip it
