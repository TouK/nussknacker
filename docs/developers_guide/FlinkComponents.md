---
title: Flink Engine Components Guide
description: Develop custom components for Flink in Nussknacker. Extend processing logic with custom sources, services, and enrichers.
sidebar_label: Flink components
---

# Flink components

[Sources, sinks and custom transformations](./Basics.md#components-and-componentproviders) are based on Flink API.
In order to implement any of those you need to provide:
- a Flink `DataStreamSource` in case of sources and a `DataStream` into `DataStreamSink` transformation in case of sinks
- a Nussknacker [specification](./Components.md#specification)

## Sources

### Standard implementation
The recommended way to implement a source is through [StandardFlinkSource](https://github.com/TouK/nussknacker/blob/staging/engine/flink/components-api/src/main/scala/pl/touk/nussknacker/engine/flink/api/process/FlinkSource.scala)
interface. Your source only has to implement a `sourceStream` method that provides [DataStreamSource](https://nightlies.apache.org/flink/flink-docs-master/api/java/org/apache/flink/streaming/api/datastream/DataStreamSource.html)
based on [StreamExecutionEnvironment](https://nightlies.apache.org/flink/flink-docs-master/api/java/org/apache/flink/streaming/api/environment/StreamExecutionEnvironment.html).

This approach provides a standard transformation of the input value into a Nussknacker `Context` and allows the implementation to customize these steps:
- [Timestamp watermark handler](https://github.com/TouK/nussknacker/blob/staging/engine/flink/components-api/src/main/scala/pl/touk/nussknacker/engine/flink/api/timestampwatermark/TimestampWatermarkHandler.scala)
  so that events are correctly processed downstream, for example to avoid (or force!) dropping late events by aggregates. Read more about
  [notion of time](../scenarios_authoring/DataSourcesAndSinks.md#notion-of-time--flink-engine-only)
  and [watermarks](https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/datastream/event-time/generating_watermarks/)
- [Context initializer](https://github.com/TouK/nussknacker/blob/staging/components-api/src/main/scala/pl/touk/nussknacker/engine/api/process/ContextInitializer.scala)
  to emit more variables than `#input`. For example built-in Kafka sources emit `#inputMeta` variable with Kafka record metadata like: partition, topic, offset, etc.
  The other example could be a file source that emits current line number as a new variable along with the content (as `#input` variable)

### Generic implementation
Nussknacker also provides a more generic interface for implementing sources - [FlinkSource](https://github.com/TouK/nussknacker/blob/staging/engine/flink/components-api/src/main/scala/pl/touk/nussknacker/engine/flink/api/process/FlinkSource.scala).
Instead of providing a Flink `DataStreamSource`, you can provide an arbitrary `DataStream[Context]` directly. However, you 
have to remember to assign timestamps, watermarks, and initialize the context.

### Test support
To enable testing functionality in scenarios using your source implementation, your source needs to implement certain test-specific interfaces:
- Basic test support - `FlinkSourceTestSupport` - besides the more general `SourceTestSupport`, the implementation: 
    - has to provide a Flink `TypeInformation` for serializing/deserializing data emitted from source (e.g. `#input`)
    - optionally can provide a `TimestampWatermarkHandler` that will be used only for tests
- Test data generation - `TestDataGenerator`
- Ad hoc test support - `TestWithParametersSupport`

Read more about testing functionality in [this section](../scenarios_authoring/TestingAndDebugging.md).

### Specification
Your Nussknacker source component specification should be a [SourceFactory](https://github.com/TouK/nussknacker/blob/staging/components-api/src/main/scala/pl/touk/nussknacker/engine/api/process/Source.scala)
returning your source implementation.

### Examples
- [Event generator source](../scenarios_authoring/DataSourcesAndSinks.md#event-generator) and its [implementation](https://github.com/TouK/nussknacker/blob/staging/engine/flink/components/base/src/main/scala/pl/touk/nussknacker/engine/flink/util/transformer/EventGeneratorSourceFactory.scala)
- [FlinkKafkaSource](https://github.com/TouK/nussknacker/blob/staging/engine/flink/kafka-components-utils/src/main/scala/pl/touk/nussknacker/engine/kafka/source/flink/FlinkKafkaSource.scala)
  and its factory returning the source implementation along with the fixed specification (e.g. based on a Scala case class) [KafkaSourceFactory](https://github.com/TouK/nussknacker/blob/staging/utils/kafka-components-utils/src/main/scala/pl/touk/nussknacker/engine/kafka/source/KafkaSourceFactory.scala)
  or generic one [UniversalKafkaSourceFactory](https://github.com/TouK/nussknacker/blob/staging/utils/schemed-kafka-components-utils/src/main/scala/pl/touk/nussknacker/engine/schemedkafka/source/UniversalKafkaSourceFactory.scala)
  reading Kafka in different formats: Avro or Json with schemas defined in Schema Registry.

Sources for various systems like RabbitMQ, JDBC, etc. do not necessarily have to be implemented from scratch. Flink comes with
simple sources [already predefined](https://ci.apache.org/projects/flink/flink-docs-master/docs/dev/datastream/overview/#data-sources)
and [connectors](https://ci.apache.org/projects/flink/flink-docs-master/docs/connectors/datastream/overview) with third-party systems.
All of them can be used to implement a Nussknacker source.

## Sinks

### Implementation
Sinks are easier to implement than sources. Nussknacker provides a [factory](https://github.com/TouK/nussknacker/blob/staging/engine/flink/components-utils/src/main/scala/pl/touk/nussknacker/engine/flink/util/sink/SingleValueSinkFactory.scala)
for sinks that take only one parameter. The only thing that has to be provided is a Flink [SinkFunction](https://nightlies.apache.org/flink/flink-docs-stable/api/java/org/apache/flink/streaming/api/functions/sink/SinkFunction.html).

Sinks with multiple parameters can be implemented using [FlinkSink](https://github.com/TouK/nussknacker/blob/staging/engine/flink/components-api/src/main/scala/pl/touk/nussknacker/engine/flink/api/process/FlinkSink.scala).
The following things are required:
- `prepareValue` - a method that turns `DataStream[Context]` into `DataStream[ValueWithContext[Value]]` containing a final, evaluated value for the sink
- `registerSink` - a method that turns `DataStream[ValueWithContext[Value]]` into `DataStreamSink`. It's the place where
  a Flink `SinkFunction` should be registered

### Specification
Similarly to sources, all sinks returned by [SinkFactory](https://github.com/TouK/nussknacker/blob/staging/components-api/src/main/scala/pl/touk/nussknacker/engine/api/process/Sink.scala)
have to implement `FlinkSink` (or its subtrait `BasicFlinkSink`).

### Examples
- [FlinkKafkaUniversalSink](https://github.com/TouK/nussknacker/blob/staging/engine/flink/schemed-kafka-components-utils/src/main/scala/pl/touk/nussknacker/engine/schemedkafka/sink/flink/FlinkKafkaUniversalSink.scala)
  and its factory [UniversalKafkaSinkFactory](https://github.com/TouK/nussknacker/blob/staging/utils/schemed-kafka-components-utils/src/main/scala/pl/touk/nussknacker/engine/schemedkafka/sink/UniversalKafkaSinkFactory.scala)

Flink provides [basic](https://ci.apache.org/projects/flink/flink-docs-master/docs/dev/datastream/overview/#data-sinks) sinks
and [connectors](https://ci.apache.org/projects/flink/flink-docs-master/docs/connectors/datastream/overview) which can be used while implementing
own Nussknacker sinks.

## Custom stream transformations

Custom transformation can arbitrarily change `DataStream[Context]`, it is implemented with [FlinkCustomStreamTransformation](https://github.com/TouK/nussknacker/blob/staging/engine/flink/components-api/src/main/scala/pl/touk/nussknacker/engine/flink/api/process/FlinkCustomStreamTransformation.scala).
Great examples of custom transformers are [aggregates](../scenarios_authoring/AggregatesInTimeWindows.md). [See here](https://github.com/TouK/nussknacker/tree/staging/engine/flink/components/base/src/main/scala/pl/touk/nussknacker/engine/flink/util/transformer)
how components like [previousValue](../scenarios_authoring/DataSourcesAndSinks.md#previousvalue), [delay](../scenarios_authoring/DataSourcesAndSinks.md#delay)
and aggregates are implemented.

### Multiple outputs

A custom transformer declares its outputs as one ordered list - the head is the main output, the tail are additional,
named outputs, via `CustomStreamTransformer.outputs`:

```scala
import eu.timepit.refined.auto._ // required for the "passed" literal to become a ComponentOutput name

override def outputs: NonEmptyList[ComponentOutput] = NonEmptyList.of(ComponentOutput("passed"), ComponentOutput.RejectedOutput)
```

The main output's name is shown only when the node has additional outputs; a component keeping the default
(`ComponentOutput.MainOutput`) looks and behaves as a plain single-output node. Names must be unique across the
whole declaration, the main output's included.

On Flink, a component with only the main output implements `FlinkCustomStreamTransformation` and returns that one
stream from `transform`, as a class or through the `FlinkCustomStreamTransformation(fun)` factories.
Registration wires that stream to the main output whatever the component named it.
A component with additional outputs implements the unrelated `FlinkMultiOutputStreamTransformation` instead, whose
`transform` returns a non-empty list of the node's streams, each keyed by the output it belongs to
(additional outputs are usually side outputs read back via `FlinkCustomNodeContext.createOutputTag`).
Job registration looks the streams up by key, so their order does not matter.
The main output always needs exactly one stream, whether or not the scenario wires it; an additional output needs one
only when the scenario connects it, and the streams of the unconnected ones are never read.
Omitting a stream that is needed, or returning two under the same key, fails registration.
A wrong key does not: the keys are the only signal of which stream belongs to which output, so pair them
deliberately - a mixed-up pairing routes records down the wrong branches with no error at any stage.
Declaring additional outputs and returning a `FlinkCustomStreamTransformation` fails earlier, at scenario compilation,
as soon as a scenario connects one of them.
Flink truncates operator names to 80 characters in metric scopes; an additional output's `$output-<name>` marker leads
its operator name, so the branches stay distinguishable in operator-scoped metrics unless the scenario name alone
approaches that limit.
See `DeduplicationTransformer` for a complete example: it declares `passed`/`rejected` and routes rejected records as a side output.

## Common details

Access to metadata like node id or scenario name and various helpers is provided by `FlinkCustomNodeContext`.

Special care should be taken to handle:
- lifecycle - preparing the operator (source, sink or functions registered by custom transformers), closing resources, handling failures and restoring the state.
  See Flink's operators lifecycle for more details [here](https://ci.apache.org/projects/flink/flink-docs-master/docs/internals/task_lifecycle/)
- exceptions, e.g. during deserialization, since any thrown and unhandled exception by the source, causes the Flink job to restart.

:warning: **Flink components should not extend Lifecycle** - it won't be handled properly
