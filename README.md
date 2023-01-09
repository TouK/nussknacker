<div align="center">
  <img src="https://nussknacker.io/wp-content/uploads/2021/10/Nussknacker-logo-black.svg" height="50">
</div>
</br>
<h1 align="center">Real-time actions on data</h1>

<div align="center">
  
  [![Maven Central](https://maven-badges.herokuapp.com/maven-central/pl.touk.nussknacker/nussknacker-designer_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/pl.touk.nussknacker/nussknacker-designer_2.12)
  [![Build status](https://github.com/touk/nussknacker/workflows/CI/badge.svg?branch=staging)](https://github.com/touk/nussknacker/actions?query=workflow%3ACI+branch%3Astaging++)
  [![Coverage Status](https://coveralls.io/repos/github/TouK/nussknacker/badge.svg?branch=staging)](https://coveralls.io/github/TouK/nussknacker?branch=staging)
  [![Artifact HUB](https://img.shields.io/endpoint?url=https://artifacthub.io/badge/repository/touk)](https://artifacthub.io/packages/search?repo=touk)
  [![PR](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md#Contributing)

</div>
<h3 align="center">
  <a href="https://demo.nussknacker.io"><b>Demo</b></a> &bull;
  <a href="https://nussknacker.io/documentation"><b>Documentation</b></a> &bull;
  <a href="https://nussknacker.io/documentation/docs/installation_configuration_guide/Installation/"><b>Instalation</b></a> &bull;
  <a href="https://cloud.nussknacker.io"><b>Nu Cloud</b></a>  
</h3>

![image](https://nussknacker.io/documentation/assets/images/nu_designer-87526e47584a5eeb9ce59ad7509d2e7b.png)

Nussknacker lets you design, deploy and monitor real time decision algorithms with easy to use GUI.

For stateless use cases we provide lightweight, but scalable and performant engine deploying to Kubernetes. 
When more advanced, stateful scenarios are needed we can leverage the power and reliability of [Apache Flink](https://flink.apache.org/) 
to make your processes fast and accurate.

## Quickstart

If you want to see Nussknacker in action without any other dependencies, you can use embedded engine in Request-response mode (scenario logic is exposed with REST API), just run:
```bash
docker run -it --network host -e DEFAULT_SCENARIO_TYPE=request-response-embedded touk/nussknacker:latest
```
Be aware that some things (e.g. metrics) will not work, and this engine is not intended for production use.

If you want to follow step-by-step via more complex tutorials, based on production ready engines, read one of quickstart guides for: [Streaming mode on Flink engine](https://nussknacker.io/documentation/quickstart/docker/)
or [Streaming mode on Lite engine](https://nussknacker.io/documentation/quickstart/helm) or [Request-response mode on Lite engine](https://nussknacker.io/documentation/quickstart/helm-request-response)

## Contact

Talk to us on [mailing list](https://groups.google.com/forum/#!forum/nussknacker)
or [start a discussion](https://github.com/TouK/nussknacker/discussions/new?category=q-a)

## Scala compatibility

Currently, we do support Scala 2.12, we will cross publish when Flink supports Scala >= 2.13.

## Flink compatibility

We currently support only one Flink version (more or less the latest one, please see flinkV in build.sbt). 
However, it should be possible to run Nussknacker with older Flink version. 

While we don't provide out-of-the-box
support as it would complicate the build process, there is separate [repo](https://github.com/TouK/nussknacker-flink-compatibility)
with detailed instructions how to run Nussknacker with some of the older versions.  

## Related projects

- [nussknacker-quickstart](https://github.com/TouK/nussknacker-quickstart) - Repository with quick setup (docker-compose or helm) presenting typical usage of Nussknacker    
- [nussknacker-sample-components](https://github.com/touk/nussknacker-sample-components) - Start here if you intend to create own Nussknacker components
- [nussknacker-helm](https://github.com/TouK/nussknacker-helm) - Helm chart of the project                     
- [nussknacker-flink-compatibility](https://github.com/TouK/nussknacker-flink-compatibility) - Toolbox providing backward compatibility for older Flink's versions    
- [prinz-nussknacker](https://github.com/prinz-nussknacker/prinz) - Nussknacker integration with ML models and model registries - currently supports [mlflow](https://mlflow.org/), [PMML](http://dmg.org/pmml/v4-4-1/GeneralStructure.html) (via [JPMML](https://github.com/jpmml/jpmml-evaluator)) and [H2O Java models](https://docs.h2o.ai/h2o/latest-stable/h2o-genmodel/javadoc/index.html)
- [nussknacker-benchmarks](https://github.com/TouK/nussknacker-benchmarks) - micro and e2e benchmarks visualization
                                                   

## Contributing

Nussknacker is an open source project - contribution is welcome. Read how to do it in [Contributing guide](CONTRIBUTING.md).
There you can also find out how to build and run development version of Nussknacker.

## License

**Nussknacker** is published under [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).
