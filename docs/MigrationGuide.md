# Migration guide

To see biggest differences please consult the [changelog](Changelog.md).

## In version 0.4.0 (not released yet) 

* [#1346](https://github.com/TouK/nussknacker/pull/1346) `AggregatorFunction` now takes type of stored state that can be 
  `immutable.SortedMap` (previous behaviour) or `java.util.Map` (using Flink's serialization) and `validatedStoredType` parameter for 
  providing better `TypeInformation` for aggregated values
* [#1343](https://github.com/TouK/nussknacker/pull/1343) `FirstAggregator` changed serialized state, it is not compatible, 
  ```Aggregator``` trait has new method ```computeStoredType``` 
* [#1352](https://github.com/TouK/nussknacker/pull/1352) AvroStringSettings class has been introduced, which allows control
  whether Avro type ```string``` is represented by ```java.lang.String``` (also in runtime) or ```java.lang.CharSequence```
  (implemented in runtime by ```org.apache.avro.util.Utf8```). This setting is available through environment variable 
  ```AVRO_USE_STRING_FOR_STRING_TYPE```. Please mind that this setting is global - it applies to all processes running on Flink
  and also requires restarting TaskManager when changing the value.
* [#1361](https://github.com/TouK/nussknacker/pull/1361) Lazy variables are removed, you should use standard enrichers for those cases.
  Their handling has been source of many problems and they made it harder to reason about the exeuction of process.   
* [#1373](https://github.com/TouK/nussknacker/pull/1373) Creating `ClassLoaderModelData` directly is not allowed, use
  `ModelData.apply` with plain config, wrapping with ModelConfigToLoad by yourself is not needed.

## In version 0.3.0

* [#1313](https://github.com/TouK/nussknacker/pull/1313) Kafka Avro API passes `KafkaConfig` during `TypeInformation` determining
* [#1305](https://github.com/TouK/nussknacker/pull/1305) Kafka Avro API passes `RuntimeSchemaData` instead of `Schema` in various places
* [#1304](https://github.com/TouK/nussknacker/pull/1304) `SerializerWithSpecifiedClass` was moved to `flink-api` module.
* [#1044](https://github.com/TouK/nussknacker/pull/1044) Upgrade to Flink 1.11. Current watermark/timestamp mechanisms are deprectated in Flink 1.11, 
 new API ```TimestampWatermarkHandler``` is introduced, with ```LegacyTimestampWatermarkHandler``` as wrapper for previous mechanisms.
* [#1244](https://github.com/TouK/nussknacker/pull/1244) `Parameter` has new parameter 'variablesToHide' with `Set` of variable names
that will be hidden before parameter's evaluation
* [#1159](https://github.com/TouK/nussknacker/pull/1159) [#1170](https://github.com/TouK/nussknacker/pull/1170) Changes in `GenericNodeTransformation` API:
  - Now `implementation` takes additional parameter with final state value determined during `contextTransformation`
  - `DefinedLazyParameter` and `DefinedEagerParameter` holds `expression: TypedExpression` instead of `returnType: TypingResult`
  - `DefinedLazyBranchParameter` and `DefinedEagerBranchParameter` holds `expressionByBranchId: Map[String, TypedExpression]` instead of `returnTypeByBranchId: Map[String, TypingResult]`
* [#1083](https://github.com/TouK/nussknacker/pull/1083)
  - Now `SimpleSlidingAggregateTransformerV2` and `SlidingAggregateTransformer` is deprecated in favour of `SlidingAggregateTransformerV2`
  - Now `SimpleTumblingAggregateTransformer` is deprecated in favour of `TumblingAggregateTransformer`
  - Now `SumAggregator`, `MaxAggregator` and `MinAggregator` doesn't change type of aggregated value (previously was changed to Double)
  - Now `SumAggregator`, `MaxAggregator` and `MinAggregator` return null instead of `0D`/`Double.MaxValue`/`Double.MinValue` for case when there was no element added before `getResult`

* [#1149](https://github.com/TouK/nussknacker/pull/1149) FlinkProcessRegistrar refactor (can affect test code) 
* [#1166](https://github.com/TouK/nussknacker/pull/1166) ```model.conf``` should be renamed to ```defaultModelConfig.conf```
* [#1218](https://github.com/TouK/nussknacker/pull/1218) FlinkProcessManager is no longer bundled in ui uber-jar. In docker/tgz distribution
* [#1255](https://github.com/TouK/nussknacker/pull/1255) Moved displaying `Metrics tab` to `customTabs`
* [#1257](https://github.com/TouK/nussknacker/pull/1257) Improvements: Flink test util package
    - Added methods: `cancelJob`, `submitJob`, `listJobs`, `runningJobs` to `FlinkMiniClusterHolder`
    - Deprecated: `runningJobs`, from `MiniClusterExecutionEnvironment`
    - Removed: `getClusterClient` from `FlinkMiniClusterHolder` interface, because of flink compatibility at Flink 1.9 
    - Renamed: `FlinkStreamingProcessRegistrar` to `FlinkProcessManager` 
* [#1303](https://github.com/TouK/nussknacker/pull/1303) TypedObjectTypingResult has a new field: additionalInfo

## In version 0.2.0

* [#1104](https://github.com/TouK/nussknacker/pull/1104) Creation of `FlinkMiniCluster` is now extracted from `StoppableExecutionEnvironment`. You should create it using e.g.:
  ```
  val flinkMiniClusterHolder = FlinkMiniClusterHolder(FlinkTestConfiguration.configuration(parallelism))
  flinkMiniClusterHolder.start()
  ```
  and then create environment using:
  ```
  flinkMiniClusterHolder.createExecutionEnvironment()
  ```
  . At the end you should cleanup `flinkMiniClusterHolder` by:
  ```
  flinkMiniClusterHolder.stop()
  ```
  . `FlinkMiniClusterHolder` should be created once for test class - it is thread safe and resource expensive. `MiniClusterExecutionEnvironment` in the other hand should be created for each process.
  It is not thread safe because underlying `StreamExecutionEnvironment` is not. You can use `FlinkSpec` to achieve that.
* [#1077](https://github.com/TouK/nussknacker/pull/1077)
  - `pl.touk.nussknacker.engine.queryablestate.QueryableClient` was moved from `queryableState` module to `pl.touk.nussknacker.engine.api.queryablestate` package in `api` module
  - `pl.touk.nussknacker.engine.queryablestate.QueryableState` was moved to `pl.touk.nussknacker.engine.api.queryablestate`
  - CustomTransformers from `pl.touk.nussknacker.engine.flink.util.transformer` in `flinkUtil` module were moved to new `flinkModelUtil` module.
  - `pl.touk.nussknacker.engine.testing.EmptyProcessConfigCreator` was moved from `interpreter` module to `pl.touk.nussknacker.engine.util.process` package in `util` module
* [#1039](https://github.com/TouK/nussknacker/pull/1039) Generic parameter of `LazyParameter[T]` has upper bound AnyRef now to avoid problems with bad type extraction.
It caused changes `Any` to `AnyRef` in a few places - mainly `FlinkLazyParameterFunctionHelper` and `FlinkCustomStreamTransformation`
* [#1039](https://github.com/TouK/nussknacker/pull/1039) `FlinkStreamingProcessRegistrar.apply` has a new parameter of type `ExecutionConfigPreparer`.
In production code you should pass `ExecutionConfigPreparer.defaultChain()` there and in test code you should pass `ExecutionConfigPreparer.unOptimizedChain()`. See scaladocs for more info.
If you already have done some Flink's `ExecutionConfig` set up before you've registered process, you should consider create your own chain using `ExecutionConfigPreparer.chain()`.
* [#1039](https://github.com/TouK/nussknacker/pull/1039) `FlinkSourceFactory` doesn't take `TypeInformation` type class as a generic parameter now. Instead of this, it takes `ClassTag`.
`TypeInformation` is determined during source creation. `typeInformation[T]` method was moved from `BasicFlinkSource` to `FlinkSource` because still must be some place to determine it for tests purpose.
* [#965](https://github.com/TouK/nussknacker/pull/965) 'aggregate' node in generic model was renamed to 'aggregate-sliding'
* [#922](https://github.com/TouK/nussknacker/pull/922) HealthCheck API has new structure, naming and json responses:
  - old `/healthCheck` is moved to `/healthCheck/process/deployment`
  - old `/sanityCheck` is moved to `/healthCheck/process/validation`
  - top level `/healthCheck` indicates general "app-is-running" state

* [#879](https://github.com/TouK/nussknacker/pull/879) Metrics use variables by default, see [docs](https://github.com/TouK/nussknacker/blob/staging/docs/Metrics.md)
  to enable old mode, suitable for graphite protocol. To use old way of sending:
    - put `globalParameters.useLegacyMetrics = true` in each model configuration (to configure metrics sending in Flink)
    - put: 
    ```
    countsSettings {
      user: ...
      password: ...
      influxUrl: ...
      metricsConfig {
        nodeCountMetric: "nodeCount.count"
        sourceCountMetric: "source.count"
        nodeIdTag: "action"
        countField: "value"

      }
    }
    ```
* Introduction to KafkaAvro API: 
    [#871](https://github.com/TouK/nussknacker/pull/871), 
    [#881](https://github.com/TouK/nussknacker/pull/881), 
    [#903](https://github.com/TouK/nussknacker/pull/903), 
    [#981](https://github.com/TouK/nussknacker/pull/981), 
    [#989](https://github.com/TouK/nussknacker/pull/989), 
    [#998](https://github.com/TouK/nussknacker/pull/998), 
    [#1007](https://github.com/TouK/nussknacker/pull/1007), 
    [#1014](https://github.com/TouK/nussknacker/pull/1014),
    [#1034](https://github.com/TouK/nussknacker/pull/1034),
    [#1041](https://github.com/TouK/nussknacker/pull/1041)

API for `KafkaAvroSourceFactory` was changed:

`KafkaAvroSourceFactory` old way:
```
val clientFactory = new SchemaRegistryClientFactory
val source = new KafkaAvroSourceFactory(
  new AvroDeserializationSchemaFactory[GenericData.Record](clientFactory, useSpecificAvroReader = false),
  clientFactory,
  None,
  processObjectDependencies = processObjectDependencies
)
```

`KafkaAvroSourceFactory` new way :
```
val schemaRegistryProvider = ConfluentSchemaRegistryProvider(processObjectDependencies)
val source = new KafkaAvroSourceFactory(schemaRegistryProvider, processObjectDependencies, None)
```

Provided new API for Kafka Avro Sink:

```
val kafkaAvroSinkFactory = new KafkaAvroSinkFactory(schemaRegistryProvider, processObjectDependencies)
```

Additional changes:
- Bump up confluent package to 5.5.0
- (Refactor Kafka API) Moved `KafkaSourceFactory` to `pl.touk.nussknacker.engine.kafka.sink` package
- (Refactor Kafka API) Changed `BaseKafkaSourceFactory`, now it requires `deserializationSchemaFactory: KafkaDeserializationSchemaFactory[T]`
- (Refactor Kafka API) Moved `KafkaSinkFactory` to `pl.touk.nussknacker.engine.kafka.source` package
- (Refactor Kafka API) Renamed `SerializationSchemaFactory` to `KafkaSerializationSchemaFactory`
- (Refactor Kafka API) Renamed `DeserializationSchemaFactory` to `KafkaDeserializationSchemaFactory`
- (Refactor Kafka API) Renamed `FixedDeserializationSchemaFactory` to `FixedKafkaDeserializationSchemaFactory`
- (Refactor Kafka API) Renamed `FixedSerializationSchemaFactory` to `FixedKafkaSerializationSchemaFactory`
- (Refactor Kafka API) Removed `KafkaSerializationSchemaFactoryBase`
- (Refactor Kafka API) Replaced `KafkaKeyValueSerializationSchemaFactoryBase` by `KafkaAvroKeyValueSerializationSchemaFactory` (it handles only avro case now)
- (Refactor Kafka API) Removed `KafkaDeserializationSchemaFactoryBase`
- (Refactor Kafka API) Replaced `KafkaKeyValueDeserializationSchemaFactoryBase` by `KafkaAvroKeyValueDeserializationSchemaFactory` (it handles only avro case now)
- (Refactor KafkaAvro API) Renamed `AvroDeserializationSchemaFactory` to `ConfluentKafkaAvroDeserializationSchemaFactory` and moved to `avro.schemaregistry.confluent` package
- (Refactor KafkaAvro API) Renamed `AvroKeyValueDeserializationSchemaFactory` to `ConfluentKafkaAvroDeserializationSchemaFactory` and moved to `avro.schemaregistry.confluent` package
- (Refactor KafkaAvro API) Renamed `AvroSerializationSchemaFactory` to `ConfluentAvroSerializationSchemaFactory` and moved to `avro.schemaregistry.confluent` package
- (Refactor KafkaAvro API) Renamed `AvroKeyValueSerializationSchemaFactory` to `ConfluentAvroKeyValueSerializationSchemaFactory` and moved to `avro.schemaregistry.confluent` package
- (Refactor KafkaAvro API) Removed `FixedKafkaAvroSourceFactory` and `FixedKafkaAvroSinkFactory` (now we don't support fixed schema)
- (Refactor Kafka API) Replaced `topics: List[String]` by `List[PreparedKafkaTopic]` and removed `processObjectDependencies` in `KafkaSource`

Be aware that we are using avro 1.9.2 instead of default Flink's 1.8.2 (for java time logical types conversions purpose).

* [#1013](https://github.com/TouK/nussknacker/pull/1013) Expression evaluation is synchronous now. It shouldn't cause any problems 
(all languages were synchronous anyway), but some internal code may have to change.

## In version 0.1.2

* [#957](https://github.com/TouK/nussknacker/pull/957) Custom node `aggregate` from `generic` model has changed parameter 
 from `windowLengthInSeconds` to `windowLength` with human friendly duration input. If you have used it in process, you need
 to insert correct value again.
* [#954](https://github.com/TouK/nussknacker/pull/954) `TypedMap` is not a case class wrapping scala Map anymore. If you have
 done some pattern matching on it, you should use `case typedMap: TypedMap => typedMap.asScala` instead.

## In version 0.1.1

* [#930](https://github.com/TouK/nussknacker/pull/930) `DeeplyCheckingExceptionExtractor` was moved from `nussknacker-flink-util`
  module to `nussknacker-util` module.
* [#919](https://github.com/TouK/nussknacker/pull/919) `KafkaSource` constructor now doesn't take `consumerGroup`. Instead of this
 it computes `consumerGroup` on their own based on `kafka.consumerGroupNamingStrategy` in `modelConfig` (default set to `processId`).
 You can also override it by `overriddenConsumerGroup` optional parameter.
 Regards to this changes, `KafkaConfig` has new, optional parameter `consumerGroupNamingStrategy`.
* [#920](https://github.com/TouK/nussknacker/pull/920) `KafkaSource` constructor now takes `KafkaConfig` instead of using one
 that was parsed by `BaseKafkaSourceFactory.kafkaConfig`. Also if you parse Typesafe Config to `KafkaSource` on your own, now you should
 use dedicated method `KafkaConfig.parseConfig` to avoid further problems when parsing strategy will be changed.
* [#914](https://github.com/TouK/nussknacker/pull/914) `pl.touk.nussknacker.engine.api.definition.Parameter` has deprecated
 main factory method with `runtimeClass` parameter. Now should be passed `isLazyParameter` instead. Also were removed `runtimeClass`
 from variances of factory methods prepared for easy testing (`optional` method and so on).

## In version 0.1.0

* [#755](https://github.com/TouK/nussknacker/pull/755) Default async execution context does not depend on parallelism.
 `asyncExecutionConfig.parallelismMultiplier` has been deprecated and should be replaced with `asyncExecutionConfig.workers`.
 8 should be sane default value.
* [#722](https://github.com/TouK/nussknacker/pull/722) Old way of configuring Flink and model (via `flinkConfig` and `processConfig`) is removed.
 `processTypes` configuration should be used from now on. Example:
    ```
    flinkConfig {...}
    processConfig {...}
    ```
    becomes:
    ```
    processTypes {
      "type e.g. streaming" {
        engineConfig { 
          type: "flinkStreaming"
          PUT HERE PROPERTIES OF flinkConfig FROM OLD CONFIG 
        }
        modelConfig {
          classPath: PUT HERE VALUE OF flinkConfig.classPath FROM OLD CONFIG
          PUT HERE PROPERTIES OF processConfig FROM OLD CONFIG
        }
      }
    }
    ```
* [#763](https://github.com/TouK/nussknacker/pull/763) Some API traits (ProcessManager, DictRegistry DictQueryService, CountsReporter) now extend `java.lang.AutoCloseable`.
* Old way of additional properties configuration should be replaced by the new one, which is now mapped to `Map[String, AdditionalPropertyConfig]`. Example in your config:
    ```
    additionalFieldsConfig: {
      mySelectProperty {
        label: "Description"
        type: "select"
        isRequired: false
        values: ["true", "false"]
      }
    }
    ```
    becomes:
    ```
    additionalPropertiesConfig {
      mySelectProperty {
        label: "Description"
        defaultValue: "false"
        editor: {
          type: "FixedValuesParameterEditor",
          possibleValues: [
            {"label": "Yes", "expression": "true"},
            {"label": "No", "expression": "false"}
          ]
        }
      }
    }
    ```  
* [#588](https://github.com/TouK/nussknacker/pull/588) [#882](https://github.com/TouK/nussknacker/pull/882) `FlinkSource` API changed, current implementation is now `BasicFlinkSource`
* [#839](https://github.com/TouK/nussknacker/pull/839) [#882](https://github.com/TouK/nussknacker/pull/882) `FlinkSink` API changed, current implementation is now `BasicFlinkSink`
* [#841](https://github.com/TouK/nussknacker/pull/841) `ProcessConfigCreator` API changed; note that currently all process objects are invoked with `ProcessObjectDependencies` as a parameter. The APIs of `KafkaSinkFactory`, `KafkaSourceFactory`, and all their implementations were changed. `Config` is available as property of `ProcessObjectDependencies` instance.
* [#863](https://github.com/TouK/nussknacker/pull/863) `restUrl` in `engineConfig` need to be preceded with protocol. Host with port only is not allowed anymore.
* Rename `grafanaSettings` to `metricsSettings` in configuration.

## In version 0.0.12

* Upgrade to Flink 1.7
* Refactor of custom transformations, dictionaries, unions, please look at samples in example or generic to see API changes
* Considerable changes to authorization configuration, please look at sample config to see changes
* Circe is now used by default instead of Argonaut, but still can use Argonaut in Displayable

## In version 0.0.11

* Changes in CustomStreamTransformer implementation, LazyInterpreter became LazyParameter, please look at samples to see changes in API

## In version 0.0.8

* Upgrade to Flink 1.4
* Change of format of Flink cluster configuration
* Parameters of sources and sinks are expressions now - automatic update of DB is available
* Change of configuration of Grafana dashboards
* Custom processes are defined in main configuration file now
