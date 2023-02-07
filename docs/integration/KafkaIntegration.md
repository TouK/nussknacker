---
sidebar_position: 1
---

# Kafka

## Prerequisites

To better understand how Nussknacker works with Kafka, it's recommended to read the following first:
* [Kafka introduction](https://kafka.apache.org/intro)
* [Role of Schema Registry](/about/TypicalImplementation)
* [Confluent Schema Registry](https://docs.confluent.io/platform/current/schema-registry/index.html)

If you want to use Flink engine, this is also recommended:
* [Flink keyed state](https://ci.apache.org/projects/flink/flink-docs-master/docs/concepts/stateful-stream-processing/#keyed-state)

## Concepts

Kafka topics are native streaming data input to Nussknacker and the native output where results of Nussknacker scenarios processing are placed. 
In Nussknacker terminology input topics are called *sources*, output topics are called *sinks*. This section provides important details of Nussknacker's integration with Kafka and Schema Registry.

## Schema Registry integration

Nussknacker integrates with Schema Registries. It is the source of knowledge about:
- Which topics are available in Kafka sources and sinks
- What schema versions are available for topic
- How code completions and validations on types described by schemas should work
- How messages will be serialized and deserialized

Currently, Nussknacker supports two implementations of schema registries: based on *Confluent Schema Registry* and based on *Azure Schema Registry*. 
During runtime, it serializes and deserializes messages in the way that is compatible with standard Kafka serializers and deserializers 
delivered by those schema registries providers. Thanks to that you should be able to send messages to Nussknacker and read messages 
produced by Nussknacker using standard tooling available around those Schema Registries.

Given implementation is picked based on `schema.registry.url` property. By default, Confluent-based implementation is used. 
For urls ended with `.servicebus.windows.net` Azure-based implementation is used.

### Connection configuration

Configuration necessary to connect with Schema registry should be placed inside `kafkaProperties`. For detailed instruction
how configuration is handled take a look at [Configuration paragraph](#configuration)

#### Confluent-based implementation

For Confluent-based implementation you should provide at least `schema.registry.url`. If your schema registry is secured
by user and password, you should additionally provide `"basic.auth.credentials.source": USER_INFO` and `basic.auth.user.info` properties.
To read more see [Schema registry documentation](https://docs.confluent.io/platform/current/schema-registry/security/index.html#governance)

#### Azure-based implementation

For Azure-based implementation, firstly you should provide `schema.registry.url` and `schema.group` properties. First one should be
`https://<event-hubs-namespace>.servicebus.windows.net`, the second one should be the name of schema groups where will be
located all schemas used by Nussknacker.

Regarding authentication, a couple of options can be used - you can provide credential via:
`azure.tenant.id`, `azure.client.id` and `azure.client.secret` properties, or you can use one of other methods handled
by Azure's [DefaultAzureCredential](https://learn.microsoft.com/en-us/java/api/overview/azure/identity-readme?view=azure-java-stable#defaultazurecredential).
For example via Azure CLI or Azure PowerShell.

### Association between schema with topic

To properly present information about topics and version and to recognize which schema is assigned to version, Nussknacker follow conventions:
- For Confluent-based implementation it uses [TopicNameStrategy for subject names](https://docs.confluent.io/platform/current/schema-registry/serdes-develop/index.html#subject-name-strategy).
  It means that it looks for schemas available at `\<topic-name\>-(key or value)`. For example for topic `transactions`, it looks for schemas at `transactions-key` for key and `transactions-value` subject for value
- In Azure Schema Registry, subjects concept doesn't exist - schemas are grouped by the same schema name. Because of that, Nussknacker introduces
  own convention for association between schema and topic: schema name should be in format: CamelCasedTopicNameKey for keys and CamelCasedTopicNameValue for values.
  For example for `input-events` topic, schema name should be named `InputEventsKey` for key or `InputEventsValue` for value. Be aware that it may require change of schema name
  not only in Azure portal but also inside schema content - those names should be the same to make serialization works correctly
  
### Schema and payload types

By default, Nussknacker supports two combinations of schema type and payload type:

* Avro schema + Avro payload
* JSON schema + JSON payload

If you prefer using JSON payload with Avro schema, you can use `avroAsJsonSerialization` configuration setting to change that behaviour ([see Configuration for details](/docs/installation_configuration_guide/ModelConfiguration#common-kafka-configuration)).

### Schema ID

Nussknacker supports schema evolution.

For sources and sinks, you can choose which schema version should be used for syntax [suggestions and validation](/docs/integration/DataTypingAndSchemasHandling.md).

At runtime Nussknacker determines the schema version of a message value and key in the following way:
1. It checks in `key.schemaId`, `value.schemaId` and Azure-specific `content-type` headers;
2. If no such headers provided, it looks for the magic byte (0x00) and a schema id in the message, [in a format used by Confluent](https://docs.confluent.io/platform/current/schema-registry/serdes-develop/index.html#wire-format);
3. If the magic byte is not found, it assumes the schema version chosen by the user in the scenario.

### Comparison

Below you can find a quick comparison of how given schema registry types are handled:

| Schema registry type | What is used for association between schema with topic | Convention                                          | The way how schema id is passed in message                                                                    |
|----------------------|--------------------------------------------------------|-----------------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| Confluent            | Subjects                                               | Subject = \<topic-name\>-(key or value)             | For Avro: in payload in format: 0x00, 4 bytes schema id, Avro payload                                         |
|                      |                                                        |                                                     | For JSON: in  `key.schemaId` or `value.schemaId` headers                                                      |
| Azure                | Schema names                                           | Schema name = \<CamelCasedTopicName\>(Key or Value) | For Avro: In header: content-type: avro/binary+schemaId                                                       |
|                      |                                                        |                                                     | For JSON: in  `key.schemaId` or `value.schemaId` headers (only when `avroAsJsonSerialization` option enabled) |

## Configuration

### Common part
The Kafka configuration is part of the Model configuration. All the settings below should be placed relative to `scenarioTypes.ScenarioTypeName.modelConfig` key. You can find the high level structure of the configuration file [here](/docs/installation_configuration_guide/#configuration-areas)

Both streaming Engines (Lite and Flink) share some common Kafka settings this section describes them, see respective sections below for details on configuring Kafka for particular Engine (e.g. the keys where the common settings should be placed at).

### Kafka connection configuration

Important thing to remember is that Kafka server addresses/Schema Registry addresses have to be resolvable from:
- Nussknacker Designer host (to enable schema discovery and scenario testing)
- Lite/Flink engine - to be able to run job

| Name                                                                        | Importance | Type     | Default value    | Description                                                                                                                                                                                                                                                                |
|-----------------------------------------------------------------------------|------------|----------|------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| kafkaProperties."bootstrap.servers"                                         | High       | string   |                  | Comma separated list of [bootstrap servers](https://kafka.apache.org/documentation/#producerconfigs_bootstrap.servers)                                                                                                                                                     |
| kafkaProperties."schema.registry.url"                                       | High       | string   |                  | Comma separated list of schema registry urls                                                                                                                                                                                                                               |
| kafkaProperties."basic.auth.credentials.source"                             | High       | string   |                  | (Confluent-only) Source of credential e.g. `USER_INFO`                                                                                                                                                                                                                     |
| kafkaProperties."basic.auth.user.info"                                      | High       | string   |                  | (Confluent-only) User and password e.g. `some_user:some_password`                                                                                                                                                                                                          |
| kafkaProperties."schema.group"                                              | High       | string   |                  | (Azure-only) Schema group with all available schemas                                                                                                                                                                                                                       |
| kafkaProperties."azure.tenant.id"                                           | High       | string   |                  | (Azure-only) Azure's tenant id                                                                                                                                                                                                                                             |
| kafkaProperties."azure.client.id"                                           | High       | string   |                  | (Azure-only) Azure's client id                                                                                                                                                                                                                                             |
| kafkaProperties."azure.client.secret"                                       | High       | string   |                  | (Azure-only) Azure's client secret                                                                                                                                                                                                                                         |
| kafkaProperties                                                             | Medium     | map      |                  | Additional configuration of [producers](https://kafka.apache.org/documentation/#producerconfigs) or [consumers](https://kafka.apache.org/documentation/#consumerconfigs)                                                                                                   |
| useStringForKey                                                             | Medium     | boolean  | true             | Kafka message keys will be in the string format (not in Avro)                                                                                                                                                                                                              |
| kafkaEspProperties.forceLatestRead                                          | Medium     | boolean  | false            | If scenario is restarted, should offsets of source consumers be reset to latest (can be useful in test enrivonments)                                                                                                                                                       |
| topicsExistenceValidationConfig.enabled                                     | Low        | boolean  | false            | Determine if existence of topics should be validated if no [auto.create.topics.enable](https://kafka.apache.org/documentation/#brokerconfigs_auto.create.topics.enable) is false on Kafka cluster - note, that it may require permissions to access Kafka cluster settings |
| topicsExistenceValidationConfig.validatorConfig.autoCreateFlagFetchCacheTtl | Low        | duration | 5 minutes        | TTL for checking Kafka cluster settings                                                                                                                                                                                                                                    |
| topicsExistenceValidationConfig.validatorConfig.topicsFetchCacheTtl         | Low        | duration | 30 seconds       | TTL for caching list of existing topics                                                                                                                                                                                                                                    |
| topicsExistenceValidationConfig.validatorConfig.adminClientTimeout          | Low        | duration | 500 milliseconds | Timeout for communicating with Kafka cluster                                                                                                                                                                                                                               |
| schemaRegistryCacheConfig.availableSchemasExpirationTime                    | Low        | duration | 10 seconds       | How often available schemas cache will be invalidated. This determines the maximum time you'll have to wait after adding new schema or new schema version until it will be available in Designer                                                                           |
| schemaRegistryCacheConfig.parsedSchemaAccessExpirationTime                  | Low        | duration | 2 hours          | How long parsed schema will be cached after first access to it                                                                                                                                                                                                             |
| schemaRegistryCacheConfig.maximumSize                                       | Low        | number   | 10000            | Maximum entries size for each caches: available schemas cache and parsed schema cache                                                                                                                                                                                      |
| lowLevelComponentsEnabled                                                   | Medium     | boolean  | false            | Add low level (deprecated) Kafka components: 'kafka-json', 'kafka-avro', 'kafka-registry-typed-json'                                                                                                                                                                       |
| avroAsJsonSerialization                                                     | Low        | boolean  | false            | Send and receive json messages serialized using Avro schema                                                                                                                                                                                                                |

### Authentication

Under the hood Nussknacker uses `kafkaProperties` to configure standard kafka client. It means that all standard client properties
will be respected. Take a look at [Kafka security documentation](https://kafka.apache.org/090/documentation.html#security) 
to see detailed examples how connection should be configured. For example for the typical `SASL_SSL` configuration with
credential in `JAAS` format should provide configuration similar to this one:

```
kafkaProperties {
  "schema.registry.url": "http://schemaregistry:8081"
  "bootstrap.servers": "broker1:9092,broker2:9092"
  "security.protocol": "SASL_SSL"
  "sasl.mechanism": "PLAIN"
  "sasl.jaas.config": "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"some_user\" password=\"some_user\";"
}
```

In case if you use your own CA and client+server certificates authentication, you should additionally provide:
`ssl.keystore.location`, `ssl.keystore.password`, `ssl.key.password`, `ssl.truststore.location`, `ssl.truststore.password`.

To make sure if your configuration is correct, you can test it with standard kafka-cli commands 
like `kafka-console-consumer`, `kafka-console-producer`, `kafka-avro-console-consumer`, `kafka-avro-console-producer` or [kcat](https://github.com/edenhill/kcat). 

Some examples:
- [Kafka quickstart](https://kafka.apache.org/documentation/#quickstart_send)
- [Azure Event Hubs for Kafka quickstart](https://github.com/Azure/azure-event-hubs-for-kafka/tree/master/quickstart/kafka-cli)

After you'll get properly working set of properties, you just need to move it to Nussknacker's configuration. 

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


### Configuration for Flink engine

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
| avroKryoGenericRecordSchemaIdSerialization        | Low        | boolean                    | true             | Should Avro messages from topics registered in schema registry be serialized in optimized way, by serializing only schema id, not the whole schema                                                          |
            

### Configuration for Lite engine

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
