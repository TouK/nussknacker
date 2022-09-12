# Flink components

Sources, sinks and custom transformations are based on
Flink API. Access to various helpers is provided by
`FlinkCustomNodeContext`. Special care should be taken to handle:
- lifecycle - preparing the operator (source, sink or functions registered by custom transformers), closing resources, handling failures and restoring the state.
  See Flink's operators lifecycle for more details [here](https://ci.apache.org/projects/flink/flink-docs-master/docs/internals/task_lifecycle/)
- exceptions, e.g. during deserialization, since any thrown and unhandled exception by the source, causes the Flink job to restart.

:warning: **Flink components should not extend Lifecycle** - it won't be handled properly

## Sources

Sources implementations are defined with [FlinkSource](https://github.com/TouK/nussknacker/blob/staging/engine/flink/components-api/src/main/scala/pl/touk/nussknacker/engine/flink/api/process/FlinkSource.scala).
In most cases (when you only pass one variable to initial `Context`) it's easier to use `BasicFlinkSource`. This trait
requires only Flink's `SourceFunction` to be implemented, whereas `FlinkSource` allows to prepare an arbitrary `DataStream`
and add custom [context initializer](https://github.com/TouK/nussknacker/blob/staging/components-api/src/main/scala/pl/touk/nussknacker/engine/api/process/ContextInitializer.scala).
You should bear in mind to apply a watermark strategy to the stream so that events will be processed correctly downstream, e.g. in aggregates.

Sources usually emit a single variable named `#input` having some attributes. To have those attributes recognized
by the designer you need to specify them. For more information consult [components specification](Components.md#specification)
and [types](Basics.md#types) to describe (available fields, their types) the emitted `#input`.

### Test data generation

Sources optionally support generating test data to ease [scenarios development](../scenarios_authoring/TestingAndDebugging.md).
In order to support this feature, `FlinkSourceTestSupport` trait has to be implemented by the source.

### Integrating with Flink sources

Flink comes with simple sources [already predefined](https://ci.apache.org/projects/flink/flink-docs-master/docs/dev/datastream/overview/#data-sources)
and [connectors](https://ci.apache.org/projects/flink/flink-docs-master/docs/connectors/datastream/overview) with third-party systems.
All of them can be used to implement a Nussknacker source. Nussknacker has built-in support for reading Kafka records in different formats:
Avro or Json with schemas defined in Schema Registry - see [`FlinkKafkaComponentProvider`](https://github.com/TouK/nussknacker/blob/staging/engine/flink/components/kafka/src/main/scala/pl/touk/nussknacker/engine/flink/util/transformer/FlinkKafkaComponentProvider.scala).
If you do not have Schema Registry in your organization, a source reading records with fixed schema is easily doable,
for a example `real-kafka-json-SampleProduct` in [`DevProcessConfigCreator`](https://github.com/TouK/nussknacker/blob/staging/engine/flink/management/dev-model/src/main/scala/pl/touk/nussknacker/engine/management/sample/DevProcessConfigCreator.scala)
-> `sourceFactories` parses a JSON input to the model represented by a case class.

### Examples

- TODO simple source ???
- [Periodic source](../scenarios_authoring/BasicNodes.md#periodic) and its [implementation](https://github.com/TouK/nussknacker/blob/staging/engine/flink/components/base/src/main/scala/pl/touk/nussknacker/engine/flink/util/transformer/PeriodicSourceFactory.scala).
- [FlinkKafkaSource](https://github.com/TouK/nussknacker/blob/staging/engine/flink/kafka-components-utils/src/main/scala/pl/touk/nussknacker/engine/kafka/source/flink/FlinkKafkaSource.scala)
  and its factory returning the source implementation along with the fixed specification [KafkaSourceFactory](https://github.com/TouK/nussknacker/blob/staging/utils/kafka-components-utils/src/main/scala/pl/touk/nussknacker/engine/kafka/source/KafkaSourceFactory.scala)
  or generic one [UniversalKafkaSourceFactory](https://github.com/TouK/nussknacker/blob/staging/utils/schemed-kafka-components-utils/src/main/scala/pl/touk/nussknacker/engine/schemedkafka/source/UniversalKafkaSourceFactory.scala).
  Those Kafka sources also have a [context initializer](https://github.com/TouK/nussknacker/blob/staging/utils/kafka-components-utils/src/main/scala/pl/touk/nussknacker/engine/kafka/source/KafkaContextInitializer.scala)
  defined that adds additional `#inputMeta` variable with Kafka record metadata like: partition, topic, offset, etc.
- [GenericSourceWithCustomVariablesSample](https://github.com/TouK/nussknacker/blob/staging/engine/flink/management/dev-model/src/main/scala/pl/touk/nussknacker/engine/management/sample/source/GenericSourceWithCustomVariablesSample.scala)
  a source that emits defined elements in a parameter but leverages [generic node transformation](Components.md#genericnodetransformation), context initializing, generating test data, typing.

## Sinks

Sinks are defined using [FlinkSink](https://github.com/TouK/nussknacker/blob/staging/engine/flink/components-api/src/main/scala/pl/touk/nussknacker/engine/flink/api/process/FlinkSink.scala).
Again, `BasicFlinkSink` is provided for simple cases which requires implementing a Flink's `SinkFunction`
and corresponding `FlinkSink` to register arbitrary `DataStreamSink`.

Similarly, Flink provides [basic](https://ci.apache.org/projects/flink/flink-docs-master/docs/dev/datastream/overview/#data-sinks) sinks
and [connectors](https://ci.apache.org/projects/flink/flink-docs-master/docs/connectors/datastream/overview) which can be used while implementing
own Nussknacker sinks. And again, Nussknacker has built-in sinks for writing to Kafka registered in
[`FlinkKafkaComponentProvider`](https://github.com/TouK/nussknacker/blob/staging/engine/flink/components/kafka/src/main/scala/pl/touk/nussknacker/engine/flink/util/transformer/FlinkKafkaComponentProvider.scala).

Examples:
- TODO simple sink ???
- [FlinkKafkaUniversalSink](https://github.com/TouK/nussknacker/blob/staging/engine/flink/schemed-kafka-components-utils/src/main/scala/pl/touk/nussknacker/engine/schemedkafka/sink/flink/FlinkKafkaUniversalSink.scala)
  and its factory [UniversalKafkaSinkFactory](https://github.com/TouK/nussknacker/blob/staging/utils/schemed-kafka-components-utils/src/main/scala/pl/touk/nussknacker/engine/schemedkafka/sink/UniversalKafkaSinkFactory.scala)

## Custom transformers

In Flink, custom transformation can arbitrarily change `DataStream[Context]`, it's implemented with [FlinkCustomStreamTransformation](https://github.com/TouK/nussknacker/blob/staging/engine/flink/components-api/src/main/scala/pl/touk/nussknacker/engine/flink/api/process/FlinkCustomStreamTransformation.scala).
Great examples of custom transformers are [aggregates](../scenarios_authoring/AggregatesInTimeWindows.md). [See here](https://github.com/TouK/nussknacker/tree/staging/engine/flink/components/base/src/main/scala/pl/touk/nussknacker/engine/flink/util/transformer)
how components like [previousValue](../scenarios_authoring/BasicNodes.md#previousvalue), [delay](../scenarios_authoring/BasicNodes.md#delay)
and aggregates are implemented.
