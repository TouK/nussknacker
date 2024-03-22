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
  <a href="https://nussknacker.io/documentation/quickstart/lite-streaming/"><b>Quickstart</b></a> &bull;
  <a href="https://nussknacker.io/documentation/docs/installation/"><b>Installation</b></a> &bull;
  <a href="https://cloud.nussknacker.io"><b>Nu Cloud</b></a>  

</h3>

![image](https://nussknacker.io/documentation/assets/images/nu_designer-87526e47584a5eeb9ce59ad7509d2e7b.png)

## What is Nussknacker

Nussknacker is a low-code visual tool for domain experts to define and run real-time decisioning algorithms instead of implementing them in the code.

In all IT systems, no matter the domain, decisions are made all the time. Which offer should a customer get? Who should receive a marketing message? Does this device need servicing? Is this a fraud?

Algorithms for making such decisions can be implemented in the code. Especially if high performance or integration of data from many sources is needed. But those algorithms often change: we have a new product we want to promote or we learn that some strange activity is not really a fraud. That requires changes in the code that would have to be made by developers.

Instead, such decision algorithms can be run by Nussknacker, where business experts can define and change them, without the need to trouble IT.

Nussknacker can make those decisions by processing event streams or in a request-response model, providing horizontal scalability and high availability. That way it fits various use cases in microservice and/or event driven architectures.

## Why Nussknacker

An essential part of Nussknacker is a visual design tool for decision algorithms. It allows not-so-technical users – analysts or business people – to define decision logic in an imperative, easy to follow and understand way. Once authored, with a click of a button, scenarios are deployed for execution. And can be changed and redeployed anytime there’s a need.

Nussknacker supports two processing modes: streaming and request-response. In streaming mode it uses Kafka as its primary interface: input streams of data and output streams of decisions. In request-response mode it exposes HTTP endpoints with OpenAPI definitions. And since input events or requests are often not enough to make a decision, Nussknacker can enrich them with data from databases and OpenAPI endpoints.

Nussknacker can be used with its own, lightweight, Kubernetes-based [engine](/about/GLOSSARY.md#engine). It provides scalability and high availability, while being straightforward to set up and maintain. However, in advanced streaming use cases, when aggregations are needed, Nussknacker can execute decision scenarios on Flink – one of the most advanced platforms for stream processing. 

## Where to learn more

[Key features](https://nussknacker.io/documentation/about/KeyFeatures/) &nbsp;
[Processing modes](https://nussknacker.io/documentation/about/ProcessingModes/) &nbsp;
[Engines](https://nussknacker.io/documentation/about/engines/) &nbsp;
[Typical implementation](https://nussknacker.io/documentation/about/TypicalImplementationStreaming/) &nbsp;
[Authoring decision scenarios with Nussknacker](https://nussknacker.io/documentation/docs/scenarios_authoring/Intro/) &nbsp;

## Quickstart

If you want to see Nussknacker in action without any other dependencies, you can use embedded engine in Request-response mode (scenario logic is exposed with REST API), just run:
```bash
docker run -it -p 8080:8080 -p 8181:8181 -e DEFAULT_SCENARIO_TYPE=request-response-embedded touk/nussknacker:latest
```
After it started go to http://localhost:8080 and login using credentials: admin/admin.
REST endpoints of deployed scenarios will be exposed at `http://localhost:8181/scenario/<slug>`. Slug is defined in Properties, and by default it is scenario name.
Be aware that some things (e.g. metrics) will not work, and this engine is not intended for production use.

If you want to follow step-by-step via more complex tutorials, based on production ready engines, read one of quickstart guides for: [Streaming mode on Lite engine](https://nussknacker.io/documentation/quickstart/lite-streaming)
or [Streaming mode on Flink engine](https://nussknacker.io/documentation/quickstart/flink) or [Request-response mode on Lite engine](https://nussknacker.io/documentation/quickstart/lite-request-response).

## Contact

Talk to us on [mailing list](https://groups.google.com/forum/#!forum/nussknacker)
or [start a discussion](https://github.com/TouK/nussknacker/discussions/new?category=q-a)

## Scala compatibility

Currently, we do support Scala 2.12 and 2.13, we cross publish versions. Default Scala version is 2.13. Docker images (both Designer and Lite Runtime) are tagged with `_scala-2.X` suffix (e.g. `1.8.0_scala_2.13` or `latest_2.12`). 
Tags without such suffix are also published, and they point to images with default Scala version build. Please be aware of that, especially if you use `latest` image tag.

## Flink compatibility

We currently support only one Flink version (more or less the latest one, please see flinkV in build.sbt). 
However, it should be possible to run Nussknacker with older Flink version. 

While we don't provide out-of-the-box
support as it would complicate the build process, there is separate [repo](https://github.com/TouK/nussknacker-flink-compatibility)
with detailed instructions how to run Nussknacker with some of the older versions.  

## Related projects

- [nussknacker-quickstart](https://github.com/TouK/nussknacker-quickstart) - Repository with quick setup (docker-compose or helm) presenting typical usage of Nussknacker    
- [nussknacker-sample-components](https://github.com/touk/nussknacker-sample-components) - Start here if you intend to create own Nussknacker components
- [nussknacker-sample-helpers](https://github.com/touk/nussknacker-sample-helpers) - Sample project showing how to add custom helpers (user defined functions)
- [nussknacker-helm](https://github.com/TouK/nussknacker-helm) - Helm chart of the project                     
- [nussknacker-flink-compatibility](https://github.com/TouK/nussknacker-flink-compatibility) - Toolbox providing backward compatibility for older Flink's versions
- [nussknacker-benchmarks](https://github.com/TouK/nussknacker-benchmarks) - micro and e2e benchmarks visualization
- [flink-scala-2.13](https://github.com/TouK/flink-scala-2.13) - our patch for Flink, required if you want use Nussknacker built with scala 2.13
                                                   

## Contributing

Nussknacker is an open source project - contribution is welcome. Read how to do it in [Contributing guide](CONTRIBUTING.md).
There you can also find out how to build and run development version of Nussknacker.

## License

**Nussknacker** is published under [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).
