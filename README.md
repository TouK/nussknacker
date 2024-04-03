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

Nussknacker is a low-code visual tool for domain experts to build, run and monitor real-time decision algorithms instead of implementing them in the code.

In all IT systems, no matter the domain, decisions are made all the time. Which offer should a customer get? Who should receive a marketing message? Does this device need servicing? Is this a fraud?

Algorithms for making such decisions can be developed by programmers. With Nussknacker however, such decision algorithms can be authored and deployed without the need to involve IT.

An essential part of Nussknacker is a visual design tool for decision algorithms. It allows not-so-technical users, like analysts or business people, to author decision logic in an imperative, easy-to-follow and understandable way. Once authored, with a click of a button, scenarios are deployed for execution. And can be changed and redeployed anytime there’s a need.

Nussknacker supports two [processing modes](https://nussknacker.io/documentation/about/ProcessingModes/): streaming and request-response. In streaming mode, it uses Kafka as its primary interface: input streams of data and output streams of decisions. In request-response mode, it exposes HTTP endpoints with OpenAPI definitions. And since input events or requests are often not enough to make a decision, Nussknacker can enrich them with data from databases and OpenAPI endpoints.

Nussknacker can be used with its own, lightweight, Kubernetes-based [engine](https://nussknacker.io/documentation/about/engines/). It provides scalability and high availability while being straightforward to set up and maintain. However, in advanced streaming use cases, when aggregations in time windows are needed, Nussknacker can execute decision scenarios on Flink – one of the most advanced platforms for stream processing.

## Why Nussknacker

Nussknacker promises to make developing and deploying real-time decision algorithms as easy as it is with spreadsheets and data at rest. If so, domain experts and developers can focus on tasks that each of these two groups is most happy to perform - domain experts can author the decision algorithms and developers can solve problems beyond the reach of tools like Nussknacker.

We discovered that several factors heavily influence the development of algorithms that work with real-time data:
- Often these algorithms are conceptualized by domain experts and the expertize required is very domain specific. Without proper tools for converting algorithms to code, domain experts have to delegate this work to programmers, proficient in multiple tools, programming languages and technologies. This approach costs money and takes time. With Nussknacker domain experts can build the algorithm from prefabricated blocks. The trick is to make these prefabricated blocks infinitely flexible -  Nussknacker achieves this by using [SpEL](https://nussknacker.io/documentation/docs/scenarios_authoring/Intro/#spel), an easy-to-learn expression language, to express data transformations and control the flow of the algorithm.
- The algorithms may require a lot of experimentation before one gets them right. If so, the iteration time required to implement a change, deploy it, and see the result should be in single minutes if not seconds. With Nussknacker, unless the change is significant, non-technical users can achieve iteration time below one minute. 
- To support high productivity, low-code tools need to provide features used by developers in professional IDEs - Nussknacker Designer has built in syntax checking, code completion, debugging, and testing support. 
- Last but not least, Nu comes with an integrated and ready-to-use monitoring subsystem built on top of InfluxDB and Grafana.

Check out [this document](https://nussknacker.io/documentation/about/KeyFeatures/) for a concise summary of Nussknacker features. 


## Use cases

![image](./docs/NuHighLevelView.png)

Nussknacker use cases follow a common pattern: a program working on a data stream, file or in a request-response interaction style receives a set of data (event or request) and has to deliver a decision. To “take” the decision it needs to perform one or more of the following: discard irrelevant records, enrich incoming records with data from external sources, aggregate events in time windows (if working on a data stream), run one or more ML models, compute the decision and finally deliver it either as another data stream or a response.  The ‘decisions’ can be from a broad spectrum of vertical and horizontal applications:
- Is it a fraud?
- Should a loan be granted?
- Next Best Offer
- Clickstream analysis
- Real-time marketing
- ML models deployments with non-trivial pre and post-processing 
- IoT sensor readouts real-time analysis
- … 


## Where to learn more

- [Typical deployment](https://nussknacker.io/documentation/about/TypicalImplementationStreaming/)
- [Authoring decision algorithms with Nussknacker](https://nussknacker.io/documentation/docs/scenarios_authoring/Intro/) 
- [Customer success story](https://nussknacker.io/case-studies/real-time-marketing-for-a-telecom-service-provider/)

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
