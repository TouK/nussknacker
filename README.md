[![Maven Central](https://maven-badges.herokuapp.com/maven-central/pl.touk.nussknacker/nussknacker-ui_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/pl.touk.nussknacker/nussknacker-ui_2.12)
[![Build status](https://github.com/touk/nussknacker/workflows/CI/badge.svg?branch=staging)](https://github.com/touk/nussknacker/actions?query=workflow%3ACI+branch%3Astaging++)
[![Coverage Status](https://coveralls.io/repos/github/TouK/nussknacker/badge.svg?branch=staging)](https://coveralls.io/github/TouK/nussknacker?branch=staging)
[![Artifact HUB](https://img.shields.io/endpoint?url=https://artifacthub.io/badge/repository/touk)](https://artifacthub.io/packages/search?repo=touk)
[![PR](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md#Contributing)

# Nussknacker

Nussknacker lets you design, deploy and monitor streaming processes using easy to use GUI.
We leverage power, performance and reliability of [Apache Flink](https://flink.apache.org/) to make your processes fast and accurate.
See [nussknacker.io](https://nussknacker.io) to read more.

## Demo

Demo is available at [demo.nussknacker.io](https://demo.nussknacker.io)

## Documentation

Documentation is available at [nussknacker.io](https://nussknacker.io/documentation).

## Quickstart

Visit [quickstart](https://nussknacker.io/documentation/quickstart/docker/) to have a look around.

## Contact

Talk to us on [mailing list](https://groups.google.com/forum/#!forum/nussknacker)
or [start a discussion](https://github.com/TouK/nussknacker/discussions/new?category=q-a)

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
- [nussknacker-sample-components](https://github.com/touk/nussknacker-sample-components) - Start here if you intend to create own Nussknacker components 
- [nussknacker-flink-compatibility](https://github.com/TouK/nussknacker-flink-compatibility) - Toolbox providing backward compatibility for older Flink's versions    
- [prinz-nussknacker](https://github.com/prinz-nussknacker/prinz) - Nussknacker integration with ML models and model registries - currently supports [mlflow](https://mlflow.org/), [PMML](http://dmg.org/pmml/v4-4-1/GeneralStructure.html) (via [JPMML](https://github.com/jpmml/jpmml-evaluator)) and [H2O Java models](https://docs.h2o.ai/h2o/latest-stable/h2o-genmodel/javadoc/index.html) 
- [nussknacker-benchmarks](https://github.com/TouK/nussknacker-benchmarks) - micro and e2e benchmarks visualization

## Contribution

Nussknacker is an open source project - contribution is welcome. Read how to do it in [Contributing guide](CONTRIBUTING.md).
There you can also find out how to build and run development version of Nussknacker.

## License

**Nussknacker** is published under [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).
