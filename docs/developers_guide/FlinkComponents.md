# Flink components

[Sources, sinks and custom transformations](./Basics.md#components-and-componentproviders) are based on Flink API.
In order to implement any of those you need to provide:
- a Flink function or Flink `DataStream` transformation
- a Nussknacker [specification](./Components.md#specification)

## Sources

Implementing a fully functional source ([BasicFlinkSource](https://github.com/TouK/nussknacker/blob/staging/engine/flink/components-api/src/main/scala/pl/touk/nussknacker/engine/flink/api/process/FlinkSource.scala))
is more complicated than a sink since the following things has to be provided:
- a Flink [SourceFunction](https://nightlies.apache.org/flink/flink-docs-stable/api/java/org/apache/flink/streaming/api/functions/source/SourceFunction.html)
- Flink [type information](https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/datastream/fault-tolerance/serialization/types_serialization/#flinks-typeinformation-class)
  for serializing/deserializing emitted data (e.g. `#input`)
- a [timestamp watermark handler](https://github.com/TouK/nussknacker/blob/staging/engine/flink/components-api/src/main/scala/pl/touk/nussknacker/engine/flink/api/timestampwatermark/TimestampWatermarkHandler.scala)
  so that events are correctly processed downstream, for example to avoid (or force!) dropping late events by aggregates. Read more about
  [notion of time](../scenarios_authoring/Intro.md#notion-of-time)
  and [watermarks](https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/datastream/event-time/generating_watermarks/)
- (optionally) generating test data support (trait `FlinkSourceTestSupport`) to ease [scenarios authoring](../scenarios_authoring/TestingAndDebugging.md)
- (optionally) custom [context initializer](https://github.com/TouK/nussknacker/blob/staging/components-api/src/main/scala/pl/touk/nussknacker/engine/api/process/ContextInitializer.scala)
  to emit more variables than `#input`. For example built-in Kafka sources emit `#inputMeta` variable with Kafka record metadata like: partition, topic, offset, etc.
  The other example could be a file source that emits current line number as a new variable along with the content (as `#input` variable)

Nussknacker also provides a more generic [FlinkSource](https://github.com/TouK/nussknacker/blob/staging/engine/flink/components-api/src/main/scala/pl/touk/nussknacker/engine/flink/api/process/FlinkSource.scala)
for implementing sources. The difference is instead of implementing a Flink `SourceFunction`, arbitrary `DataStream[Context]`
can be returned, however you have to remember to assign timestamps, watermarks and initialize the context.

When using Flink engine, all sources returned by [SourceFactory](https://github.com/TouK/nussknacker/blob/staging/components-api/src/main/scala/pl/touk/nussknacker/engine/api/process/Source.scala)
have to implement `FlinkSource` (or its subtrait `BasicFlinkSource`).

### Examples

- [Periodic source](../scenarios_authoring/DataSourcesAndSinks.md#periodic) and its [implementation](https://github.com/TouK/nussknacker/blob/staging/engine/flink/components/base/src/main/scala/pl/touk/nussknacker/engine/flink/util/transformer/PeriodicSourceFactory.scala)
- [FlinkKafkaSource](https://github.com/TouK/nussknacker/blob/staging/engine/flink/kafka-components-utils/src/main/scala/pl/touk/nussknacker/engine/kafka/source/flink/FlinkKafkaSource.scala)
  and its factory returning the source implementation along with the fixed specification (e.g. based on a Scala case class) [KafkaSourceFactory](https://github.com/TouK/nussknacker/blob/staging/utils/kafka-components-utils/src/main/scala/pl/touk/nussknacker/engine/kafka/source/KafkaSourceFactory.scala)
  or generic one [UniversalKafkaSourceFactory](https://github.com/TouK/nussknacker/blob/staging/utils/schemed-kafka-components-utils/src/main/scala/pl/touk/nussknacker/engine/schemedkafka/source/UniversalKafkaSourceFactory.scala)
  reading Kafka in different formats: Avro or Json with schemas defined in Schema Registry.

Sources for various systems like RabbitMQ, JDBC, etc. do not necessarily have to be implemented from scratch. Flink comes with
simple sources [already predefined](https://ci.apache.org/projects/flink/flink-docs-master/docs/dev/datastream/overview/#data-sources)
and [connectors](https://ci.apache.org/projects/flink/flink-docs-master/docs/connectors/datastream/overview) with third-party systems.
All of them can be used to implement a Nussknacker source.

## Sinks

Sinks are easier to implement than sources. Nussknacker provides a [factory](https://github.com/TouK/nussknacker/blob/staging/engine/flink/components-utils/src/main/scala/pl/touk/nussknacker/engine/flink/util/sink/SingleValueSinkFactory.scala)
for sinks that take only one parameter. The only thing that has to be provided is a Flink [SinkFunction](https://nightlies.apache.org/flink/flink-docs-stable/api/java/org/apache/flink/streaming/api/functions/sink/SinkFunction.html).

Sinks with multiple parameters can be implemented using [FlinkSink](https://github.com/TouK/nussknacker/blob/staging/engine/flink/components-api/src/main/scala/pl/touk/nussknacker/engine/flink/api/process/FlinkSink.scala).
The following things are required:
- `prepareValue` - a method that turns `DataStream[Context]` into `DataStream[ValueWithContext[Value]]` containing a final, evaluated value for the sink
- `registerSink` - a method that turns `DataStream[ValueWithContext[Value]]` into `DataStreamSink`. It's the place where
  a Flink `SinkFunction` should be registered

Similarly to sources, all sinks returned by [SinkFactory](https://github.com/TouK/nussknacker/blob/staging/components-api/src/main/scala/pl/touk/nussknacker/engine/api/process/Sink.scala)
have to implement `FlinkSink` (or its subtrait `BasicFlinkSink`).

Again, Flink provides [basic](https://ci.apache.org/projects/flink/flink-docs-master/docs/dev/datastream/overview/#data-sinks) sinks
and [connectors](https://ci.apache.org/projects/flink/flink-docs-master/docs/connectors/datastream/overview) which can be used while implementing
own Nussknacker sinks.

Examples:
- [FlinkKafkaUniversalSink](https://github.com/TouK/nussknacker/blob/staging/engine/flink/schemed-kafka-components-utils/src/main/scala/pl/touk/nussknacker/engine/schemedkafka/sink/flink/FlinkKafkaUniversalSink.scala)
  and its factory [UniversalKafkaSinkFactory](https://github.com/TouK/nussknacker/blob/staging/utils/schemed-kafka-components-utils/src/main/scala/pl/touk/nussknacker/engine/schemedkafka/sink/UniversalKafkaSinkFactory.scala)

## Custom stream transformations

Custom transformation can arbitrarily change `DataStream[Context]`, it is implemented with [FlinkCustomStreamTransformation](https://github.com/TouK/nussknacker/blob/staging/engine/flink/components-api/src/main/scala/pl/touk/nussknacker/engine/flink/api/process/FlinkCustomStreamTransformation.scala).
Great examples of custom transformers are [aggregates](../scenarios_authoring/AggregatesInTimeWindows.md). [See here](https://github.com/TouK/nussknacker/tree/staging/engine/flink/components/base/src/main/scala/pl/touk/nussknacker/engine/flink/util/transformer)
how components like [previousValue](../scenarios_authoring/DataSourcesAndSinks#previousvalue), [delay](../scenarios_authoring/DataSourcesAndSinks.md#delay)
and aggregates are implemented.

## Common details

Access to metadata like node id or scenario name and various helpers is provided by `FlinkCustomNodeContext`.

Special care should be taken to handle:
- lifecycle - preparing the operator (source, sink or functions registered by custom transformers), closing resources, handling failures and restoring the state.
  See Flink's operators lifecycle for more details [here](https://ci.apache.org/projects/flink/flink-docs-master/docs/internals/task_lifecycle/)
- exceptions, e.g. during deserialization, since any thrown and unhandled exception by the source, causes the Flink job to restart.

:warning: **Flink components should not extend Lifecycle** - it won't be handled properly
