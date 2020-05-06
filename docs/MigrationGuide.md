# Migration guide

To see biggest differences please consult the [changelog](Changelog.md).

## In version 0.2.0 (not released yet)

* [#879](https://github.com/TouK/nussknacker/pull/879) Metrics use variables by default, see [docs](https://github.com/TouK/nussknacker/blob/staging/docs/Metrics.md) 
  to enable old mode, suitable for graphite protocol. To use old way of sending:
    - put `globalParameters.useLegacyMetrics = true` in each model configuration (to configure metrics sending in Flink)
    - put: ```
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
* [#871](https://github.com/TouK/nussknacker/pull/871) Added SchemaRegistryProvider. 
* [#881](https://github.com/TouK/nussknacker/pull/881) Introduction to KafkaAvroSchemaProvider.

API for `KafkaAvroSourceFactory` and `KafkaTypedAvroSourceFactory` was changed (In [#871] and [#881]):

`KafkaAvroSourceFactory` and `KafkaTypedAvroSourceFactory` old way:
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
val schemaRegistryProvider = ConfluentSchemaRegistryProvider[GenericData.Record](processObjectDependencies)
val source = new KafkaAvroSourceFactory(schemaRegistryProvider, processObjectDependencies, None)
```

`KafkaTypedAvroSourceFactory` (*class name changed*) new way:
```
val avroFixedSourceFactory = FixedKafkaAvroSourceFactory[GenericData.Record](processObjectDependencies)
```

## In version 0.1.1 (not released yet)

* [#930](https://github.com/TouK/nussknacker/pull/930) `DeeplyCheckingExceptionExtractor` was moved from `nussknacker-flink-util`
  module to `nussknacker-util` module.

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
