[![Maven Central](https://maven-badges.herokuapp.com/maven-central/pl.touk.nussknacker/nussknacker-ui_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/pl.touk.nussknacker/nussknacker-ui_2.12)
[![Build status](https://github.com/touk/nussknacker/workflows/CI/badge.svg)](https://github.com/touk/nussknacker/actions?query=workflow%3A%22CI%22)
[![Coverage Status](https://coveralls.io/repos/github/TouK/nussknacker/badge.svg?branch=staging)](https://coveralls.io/github/TouK/nussknacker?branch=staging)
[![Artifact HUB](https://img.shields.io/endpoint?url=https://artifacthub.io/badge/repository/touk)](https://artifacthub.io/packages/search?repo=touk)
[![PR](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md#Contributing)

# Nussknacker

Nussknacker lets you design, deploy and monitor streaming processes using easy to use GUI.
We leverage power, performance and reliability of [Apache Flink](https://flink.apache.org/) to make your processes fast and accurate.

Visit our [pages](https://docs.nussknacker.io) to see documentation.
Visit our [quickstart](https://docs.nussknacker.io/quickstart/docker/) to have a look around.
Talk to us on our [mailing list](https://groups.google.com/forum/#!forum/nussknacker)

## Scala compatibility

Currently we do support Scala 2.12, we will cross publish when Flink supports Scala >= 2.13.

## Flink compatibility

We currently support only one Flink version (more or less latest one, please see flinkV in build.sbt). 
However, it should be possible to run Nussknacker with older Flink version. 

While we don't provide out-of-the-box
support as it would complicate the build process, there is separate [repo](https://github.com/TouK/nussknacker-flink-compatibility)
with detailed instructions how to run Nussknacker with some of the older versions.  

## Related projects

- [nussknacker-quickstart](https://github.com/TouK/nussknacker-quickstart) - Repository with docker-compose presenting typical usage of Nussknacker    
- [nussknacker-flink-compatibility](https://github.com/TouK/nussknacker-flink-compatibility) - Toolbox providing backward compatibility for older Flink's versions    
- [prinz-nussknacker](https://github.com/prinz-nussknacker/prinz) - Nussknacker integration with ML models and model registries - currently supports [mlflow](https://mlflow.org/) 

## License

**Nussknacker** is published under [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).
