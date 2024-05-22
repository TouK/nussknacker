---
title: Engines
---

Nussknacker has two main components:
the [Designer](../GLOSSARY.md#nussknacker-designer) is used to define a scenario. After deployment,
the [Engine](../GLOSSARY.md#engine) is invoked as an operational component.
Currently, there are two engines available: Flink and Lite. Designer can work with both of them interchangeably.

The first criterion for choosing an engine is [Processing mode](../ProcessingModes.md) - Request-Response mode is 
supported only by Lite engine.

For Streaming, the choice is more complex. If you want to use stateful stream processing *) - you should pick Flink.
On the other hand, the maintenance of a Flink cluster can be painful and requires a lot of hardware resources, which 
can be costly. If you want to avoid that, you should pick Lite engine. It delivers most of Nussknacker's features, 
but at the same time offers cheap and light setup. It requires a Kubernetes cluster to run scenarios.

### When Flink?
 * You need to implement decision algorithms, even highly branched
 * Your business logic requires data aggregation, joins or other ways of stateful stream processing
 * You want to match against event patterns
 * You have already running Flink cluster

### When Lite?
 * You need to implement decision algorithms, even highly branched
 * You want to invoke your scenarios not only running over streams, but also using request-response approach
 * Lightweight infrastructure is important for you
 * You have already running K8 cluster

## Features comparison
|                                                                                      | Flink Engine       | Lite Engine        |
|--------------------------------------------------------------------------------------|--------------------|--------------------|
| real-time streaming                                                                  | :heavy_check_mark: | :heavy_check_mark: |
| request-response processing                                                          | :x:                | :heavy_check_mark: |
| scalable processing (both horizontally/vertically)                                   | :heavy_check_mark: | :heavy_check_mark: |
| high availability                                                                    | :heavy_check_mark: | :heavy_check_mark: |
| high throughput                                                                      | :heavy_check_mark: | :heavy_check_mark: |
| low latency                                                                          | :heavy_check_mark: | :heavy_check_mark: |
| resilience                                                                           | :heavy_check_mark: | :heavy_check_mark: |
| metrics                                                                              | :heavy_check_mark: | :heavy_check_mark: |
| [stateful](../../scenarios_authoring/AggregatesInTimeWindows.md#concepts) processing | :heavy_check_mark: | :x:                |
| ease of operations                                                                   | medium             | easy               |
| environment setup difficulty                                                         | medium             | easy               |

*) Refer to [this](https://nightlies.apache.org/flink/flink-docs-master/docs/concepts/stateful-stream-processing/) page 
to understand what is meant by stateful stream processing. Aggregates in time windows are a good example of stateful 
stream processing
