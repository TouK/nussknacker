---
sidebar_position: 1
---

# Kafka

## Prerequisites

To fully understand how Nussknacker works with Kafka topics, it's best to read the following first:
* [Kafka introduction](https://kafka.apache.org/intro)
* [Role of Schema Registry](/about/TypicalImplementation)
* [Confluent Schema Registry](https://docs.confluent.io/platform/current/schema-registry/index.html)

If you want to use Flink engine, this is also recommended:
* [Flink keyed state](https://ci.apache.org/projects/flink/flink-docs-master/docs/concepts/stateful-stream-processing/#keyed-state)

## Sources and sinks

Kafka topics are native streaming data input to Nussknacker and the native output where results of Nussknacker scenarios processing are placed. In Nussknacker terminology input topics are called sources, output topics are called sinks. This section provides important details of Nussknacker's integration with Kafka and schema registry.

## Schema registry integration

Nussknacker integrates with a schema registry. It is the source of topics available to choose from in sources and sinks. It also allows Nussknacker to provide syntax suggestions and validation. Nussknacker assumes that for the topic `topic-name` a schema `topic-name-value` and optionally `topic-name-key` (for the Kafka topic key) will be defined in the schema registry.

Schemas are stored and managed by Confluent Schema Registry; it is [bundled with Nussknacker](/about/TypicalImplementation) in all deployment versions. Schemas can be registered in Schema Registry by means of REST API based CLI or using AKHQ, an open source GUI for Apache Kafka and Confluent Schema Registry. AKHQ is bundled with Nussknacker in all deployment versions.

Nussknacker supports both JSON and AVRO schemas, and JSON and AVRO topic payloads. Detailed information on how AVRO data should be serialized/deserialized can be found in [Confluent Wire Documentation](https://docs.confluent.io/platform/current/schema-registry/serdes-develop/index.html#wire-format).

### Schema and payload types

By default, Nussknacker supports two combinations of schema type and payload type:

* AVRO schema + AVRO payload
* JSON schema + JSON payload

If you prefer using JSON payload with AVRO schema, you can use `avroAsJsonSerialization` configuration setting to change that behaviour ([see Configuration for details](/docs/installation_configuration_guide/ModelConfiguration#common-kafka-configuration)).

### Schema ID

Nussknacker supports schema evolution.

In the Designer the user can choose, for each source and sink, which schema version should be used for syntax suggestions and validation.

At runtime Nussknacker determines the schema version of a message value and key in the following way:
1. it checks `value.schemaId` and `key.schemaId` headers;
2. if there are no such headers, it looks for a magic byte and schema version in the message, [in a format used by Confluent](https://docs.confluent.io/platform/current/schema-registry/serdes-develop/index.html#wire-format);
3. if the magic byte is not found, it assumes the schema version chosen by the user in the scenario.

  
## Configuration

### Common part
The Kafka configuration is part of the Model configuration. All the settings below should be placed relative to `scenarioTypes.ScenarioTypeName.modelConfig` key. You can find the high level structure of the configuration file [here](/docs/installation_configuration_guide/#configuration-areas)

Both streaming Engines (Lite and Flink) share some common Kafka settings this section describes them, see respective sections below for details on configuring Kafka for particular Engine (e.g. the keys where the common settings should be placed at).

### Kafka connection configuration

Important thing to remember is that Kafka server addresses/schema registry addresses have to be resolvable from:
- Nussknacker Designer host (to enable schema discovery and scenario testing)
- Lite/Flink engine - to be able to run job

| Name                                                                        | Importance | Type     | Default value    | Description                                                                                                                                                                                                                                                  |
|-----------------------------------------------------------------------------|------------|----------|------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| kafkaProperties."bootstrap.servers"                                         | High       | string   |                  | Comma separated list of [bootstrap servers](https://kafka.apache.org/documentation/#producerconfigs_bootstrap.servers)                                                                                                                                       |
| kafkaProperties."schema.registry.url"                                       | High       | string   |                  | Comma separated list of [schema registry](https://docs.confluent.io/platform/current/schema-registry/index.html)                                                                                                                                             |
| kafkaProperties                                                             | Medium     | map      |                  | Additional configuration of [producers](https://kafka.apache.org/documentation/#producerconfigs) or [consumers](https://kafka.apache.org/documentation/#consumerconfigs)                                                                                     |
| useStringForKey                                                             | Medium     | boolean  | true             | Should we assume that Kafka message keys are in plain string format (not in Avro)                                                                                                                                                                            |
| kafkaEspProperties.forceLatestRead                                          | Medium     | boolean  | false            | If scenario is restarted, should offsets of source consumers be reset to latest (can be useful in test enrivonments)                                                                                                                                         |
| topicsExistenceValidationConfig.enabled                                     | Low        | boolean  | false            | Should we validate existence of topics if no [auto.create.topics.enable](https://kafka.apache.org/documentation/#brokerconfigs_auto.create.topics.enable) is false on Kafka cluster - note, that it may require permissions to access Kafka cluster settings |
| topicsExistenceValidationConfig.validatorConfig.autoCreateFlagFetchCacheTtl | Low        | duration | 5 minutes        | TTL for checking Kafka cluster settings                                                                                                                                                                                                                      |
| topicsExistenceValidationConfig.validatorConfig.topicsFetchCacheTtl         | Low        | duration | 30 seconds       | TTL for caching list of existing topics                                                                                                                                                                                                                      |
| topicsExistenceValidationConfig.validatorConfig.adminClientTimeout          | Low        | duration | 500 milliseconds | Timeout for communicating with Kafka cluster                                                                                                                                                                                                                 |
| schemaRegistryCacheConfig.availableSchemasExpirationTime                    | Low        | duration | 10 seconds       | How often available schemas cache will be invalidated. This determines the maximum time you'll have to wait after adding new schema or new schema version until it will be available in Designer                                                             |
| schemaRegistryCacheConfig.parsedSchemaAccessExpirationTime                  | Low        | duration | 2 hours          | How long parsed schema will be cached after first access to it                                                                                                                                                                                               |
| schemaRegistryCacheConfig.maximumSize                                       | Low        | number   | 10000            | Maximum entries size for each caches: available schemas cache and parsed schema cache                                                                                                                                                                        |
| lowLevelComponentsEnabled                                                   | Medium     | boolean  | false            | Add low level (deprecated) kafka components: 'kafka-json', 'kafka-avro', 'kafka-registry-typed-json'                                                                                                                                                         |
| avroAsJsonSerialization                                                     | Low        | boolean  | false            | Send and receive json messages serialized using Avro schema                                                                                                                                                                                                  |

### Exception handling

Errors can be sent to specified Kafka topic in following json format (see below for format configuration options): 
```json
{
  "processName" : "Premium Customer Scenario",
  "nodeId" : "filter premium customers",
  "message" : "Unknown exception",
  "exceptionInput" : "SpelExpressionEvaluationException:Expression [1/0 != 10] evaluation failed, message: / by zero",
  "inputEvent" : "{ \" field1\": \"vaulue1\" }",
  "stackTrace" : "pl.touk.nussknacker.engine.api.exception.NonTransientException: mess\n\tat pl.touk.nussknacker.engine.kafka.exception.KafkaExceptionConsumerSerializationSpec.<init>(KafkaExceptionConsumerSerializationSpec.scala:24)\n\tat java.base/jdk.internal.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)\n\tat java.base/jdk.internal.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:62)\n\tat java.base/jdk.internal.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:45)\n\tat java.base/java.lang.reflect.Constructor.newInstance(Constructor.java:490)\n\tat java.base/java.lang.Class.newInstance(Class.java:584)\n\tat org.scalatest.tools.Runner$.genSuiteConfig(Runner.scala:1431)\n\tat org.scalatest.tools.Runner$.$anonfun$doRunRunRunDaDoRunRun$8(Runner.scala:1239)\n\tat scala.collection.immutable.List.map(List.scala:286)\n\tat org.scalatest.tools.Runner$.doRunRunRunDaDoRunRun(Runner.scala:1238)\n\tat org.scalatest.tools.Runner$.$anonfun$runOptionallyWithPassFailReporter$24(Runner.scala:1033)\n\tat org.scalatest.tools.Runner$.$anonfun$runOptionallyWithPassFailReporter$24$adapted(Runner.scala:1011)\n\tat org.scalatest.tools.Runner$.withClassLoaderAndDispatchReporter(Runner.scala:1509)\n\tat org.scalatest.tools.Runner$.runOptionallyWithPassFailReporter(Runner.scala:1011)\n\tat org.scalatest.tools.Runner$.run(Runner.scala:850)\n\tat org.scalatest.tools.Runner.run(Runner.scala)\n\tat org.jetbrains.plugins.scala.testingSupport.scalaTest.ScalaTestRunner.runScalaTest2or3(ScalaTestRunner.java:38)\n\tat org.jetbrains.plugins.scala.testingSupport.scalaTest.ScalaTestRunner.main(ScalaTestRunner.java:25)",
  "timestamp" : 1623758738000,
  "host" : "teriberka.pl",
  "additionalData" : {
    "scenarioCategory" : "Marketing"
  }
}
```


Following properties can be configured (please look at correct engine page : [Lite](/docs/installation_configuration_guide/model/Lite#exception-handling) or [Flink](/docs/installation_configuration_guide/model/Flink#configuring-exception-handling), 
to see where they should be set):

| Name                   | Default value | Description                                                                                                                                                                                                                                                                                                                |
|------------------------|---------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| topic                  | -             | Topic where errors will be sent. If the topic does not exist, it will be created (with default settings - for production deployments make sure the config is ok) during deploy. If (e.g. due to ACL settings) the topic cannot be created, the scenarios will fail, in that case, the topic has to be configured manually. |
| stackTraceLengthLimit  | 50            | Limit of stacktrace length that will be sent (0 to omit stacktrace at all)                                                                                                                                                                                                                                                 |
| includeHost            | true          | Should name of host where error occurred (e.g. TaskManager in case of Flink) be included. Can be misleading if there are many network interfaces or hostname is improperly configured)                                                                                                                                     |
| includeInputEvent      | false         | Should input event be serialized (can be large or contain sensitive data so use with care)                                                                                                                                                                                                                                 |
| useSharedProducer      | false         | For better performance shared Kafka producer can be used (by default it's created and closed for each error), shared Producer is kind of experimental feature and should be used with care                                                                                                                                 |
| additionalParams       | {}            | Map of fixed parameters that can be added to Kafka message                                                                                                                                                                                                                                                                 |


## Configuration for Flink engine

With Flink engine, the Kafka sources and sinks are configured as any other component. In particular,
you can configure multiple Kafka component providers - e.g. when you want to connect to multiple clusters.
Below we give two example configurations, one for default setup with one Kafka cluster and standard component names:
```
components.kafka {
  config: {
    kafkaProperties {
      "bootstrap.servers": "kafakaBroker1.sample.pl:9092,kafkaBroker2.sample.pl:9092"
      "schema.registry.url": "http://schemaRegistry.pl:8081"
    }
  }
}
```
And now - more complex, with two clusters. In the latter case, we configure prefix which will be added to component names, 
resulting in `clusterA-kafka-avro` etc.

```
components.kafkaA {
  providerType: "kafka"
  componentPrefix: "clusterA-"
  config: {
    kafkaProperties {
      "bootstrap.servers": "clusterA-broker1.sample.pl:9092,clusterA-broker2.sample.pl:9092"
      "schema.registry.url": "http://clusterA-schemaRegistry.pl:8081"
    }
  }
}
components.kafkaB {
  providerType: "kafka"
  componentPrefix: "clusterB-"
  config: {
    kafkaProperties {
      "bootstrap.servers": "clusterB-broker1.sample.pl:9092,clusterB-broker2.sample.pl:9092"
      "schema.registry.url": "http://clusterB-schemaRegistry.pl:8081"
    }
  }
}
```
 
Important thing to remember is that Kafka server addresses/schema registry addresses have to be resolvable from:
- Nussknacker Designer host (to enable schema discovery and scenario testing)
- Flink cluster (both jobmanagers and taskmanagers) hosts - to be able to run job

See [common config](../ModelConfiguration#kafka-connection-configuration) for the details of Kafka configuration, the table below presents additional options available only in Flink engine:
      

| Name                                              | Importance | Type                       | Default value    | Description                                                                                                                                                                                                 |
|---------------------------------------------------|------------|----------------------------|------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| kafkaEspProperties.defaultMaxOutOfOrdernessMillis | Medium     | duration                   | 60s              | Configuration of [bounded of orderness watermark generator](https://ci.apache.org/projects/flink/flink-docs-stable/docs/dev/datastream/event-time/built_in/#fixed-amount-of-lateness) used by Kafka sources |
| consumerGroupNamingStrategy                       | Low        | processId/processId-nodeId | processId-nodeId | How consumer groups for sources should be named                                                                                                                                                             |
| avroKryoGenericRecordSchemaIdSerialization        | Low        | boolean                    | true             | Should AVRO messages from topics registered in schema registry be serialized in optimized way, by serializing only schema id, not the whole schema                                                          |
            

## Configuration for Lite engine

The Lite engine in Streaming processing mode uses Kafka as it's core part (e.g. delivery guarantees are based on Kafka transactions), so it's configured separately from other components. Therefore, it's only possible to use one Kafka cluster for one model configuration. This configuration is used for all
Kafka based sources and sinks (you don't need to configure them separately). See [common config](#kafka-connection-configuration) for the details.
```
modelConfig {
  kafka {
    kafkaProperties {
      "bootstrap.servers": "broker1:9092,broker2:9092"
      "schema.registry.url": "http://schemaregistry:8081"
    }
  }
}  
```