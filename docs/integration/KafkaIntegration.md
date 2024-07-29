---
sidebar_position: 1
---

# Kafka

## Prerequisites

To better understand how Nussknacker works with Kafka, it's recommended to read the following first:
* [Kafka introduction](https://kafka.apache.org/intro)
* [Role of Schema Registry](../about/typical%20implementation/Streaming.md)
* [Confluent Schema Registry](https://docs.confluent.io/platform/current/schema-registry/index.html)

If you want to use Flink engine, this is also recommended:
* [Flink keyed state](https://ci.apache.org/projects/flink/flink-docs-master/docs/concepts/stateful-stream-processing/#keyed-state)

## Concepts

### Sources and sinks

Kafka topics are native streaming data input to Nussknacker and the native output where results of Nussknacker scenarios processing are placed. 
In Nussknacker terminology input topics are handled by *source* components, output topics are handled by *sink* components.
This section provides important details of Nussknacker's integration with Kafka and Schema Registry.

### Schemas

Schema defines the format of data. Nussknacker expects that messages in topics are described by the schema.
Nussknacker uses information contained in schemas for code completion and validation of messages.
Schema of message can be described in [Avro Schema format](https://avro.apache.org/docs/#schemas) or 
[JSON Schema format](https://json-schema.org) (Confluent Schema Registry only)

Schemas are managed by Schema Registry - *Confluent Schema Registry* and *Azure Schema Registry* are supported.

To preview schemas or add a new version, you can use tools available on your cloud platform or tools like [AKHQ](https://akhq.io). 
The [Nussknacker Quickstart](https://github.com/TouK/nussknacker-quickstart/tree/main) comes with bundled AKHQ,
so you can add and modify schemas without the need to install additional tools. The link to AKHQ is available in 
the Designer in Scenarios tab under Data label. 

#### Association between schema with topic

To properly present information about topics and version and to recognize which schema is assigned to version, Nussknacker follow conventions:
- For Confluent-based implementation it uses [TopicNameStrategy for subject names](https://docs.confluent.io/platform/current/schema-registry/serdes-develop/index.html#subject-name-strategy).
  It means that it looks for schemas available at `<topic-name>-(key or value)` *subject*. For example for topic `transactions`, it looks for schemas at `transactions-key` *subject* for key and `transactions-value` *subject* for value
- In the Azure Schema Registry, *subject* concept doesn't exist - schemas are grouped by the same *schema name*. Because of that, Nussknacker introduces
  own convention for association between schema and topic: *schema name* should be in format: `CamelCasedTopicNameKey` for keys and `CamelCasedTopicNameValue` for values.
  For example for `input-events` (or `input.events`) topic, schema name should be named `InputEventsKey` for key or `InputEventsValue` for value. Be aware that it may require change of schema name
  not only in Azure portal but also inside schema content - those names should be the same to make serialization works correctly

## Connection and Authentication Configuration

Under the hood Nussknacker uses `kafkaProperties` to configure standard kafka client. It means that all standard Kafka client properties will be respected.
For detailed instruction where it should be placed inside Nussknacker's configuration, take a look at [Configuration details section](#configuration-details)

### Kafka - Connection

To configure connection to kafka, you need to configure at least `bootstrap.servers` property. It should contain comma separated list of urls to Kafka brokers.

#### Kafka - Authentication

Kafka cluster has multiple options to configure Authentication. Take a look at [Kafka security documentation](https://kafka.apache.org/090/documentation.html#security)
to see detailed examples how those options should be translated into properties. For example for the typical `SASL_SSL` configuration with
credential in `JAAS` format, you should provide configuration similar to this one:

```
kafkaConfig {
  kafkaProperties {
    "schema.registry.url": "http://schemaregistry:8081"
    "bootstrap.servers": "broker1:9092,broker2:9092"
    "security.protocol": "SASL_SSL"
    "sasl.mechanism": "PLAIN"
    "sasl.jaas.config": "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"some_user\" password=\"some_user\";"
  }
}
```

If you use Azure Events Hubs (which uses this mode), username will be `$ConnectionString` and password will be the connection string starting with `Endpoint=sb://`.

In case if you use your own CA and client+server certificates authentication, you should additionally provide:
`ssl.keystore.location`, `ssl.keystore.password`, `ssl.key.password`, `ssl.truststore.location`, `ssl.truststore.password`.

To make sure if your configuration is correct, you can test it with standard kafka-cli commands
like `kafka-console-consumer`, `kafka-console-producer` or [kcat](https://github.com/edenhill/kcat).

Some tutorials how to do that:
- [Kafka quickstart](https://kafka.apache.org/documentation/#quickstart_send)
- [Azure Event Hubs for Kafka quickstart](https://github.com/Azure/azure-event-hubs-for-kafka/tree/master/quickstart/kafka-cli)

After you'll get properly working set of properties, you just need to copy it to Nussknacker's configuration.

### Schema Registry - Connection

Currently, Nussknacker supports two implementations of Schema Registries: based on *Confluent Schema Registry* and based on *Azure Schema Registry*.

To configure connection Schema Registry, you need to configure at least `schema.registry.url`. It should contain comma separated list of urls to Schema Registry.
For the single node installation, it will be just an url. Be aware that contrary to Kafka brokers, Schema Registry urls should start with `https://` or `http://`.

Nussknacker determines which registry implementation (Confluent or Azure) is used from the `schema.registry.url` property. 
If the URL ends with `.servicebus.windows.net`, Nussknacker assumes that Azure schema registry is used; if not Confluent schema registry is assumed.

#### Confluent-based Schema Registry - Connection and Authentication

For Confluent-based implementation you should provide at least `schema.registry.url`. If your schema registry is secured
by user and password, you should additionally provide `"basic.auth.credentials.source": USER_INFO` and `"basic.auth.user.info": "some_user:some_password"` entries.
To read more see [Schema registry documentation](https://docs.confluent.io/platform/current/schema-registry/security/index.html#governance)

To make sure if your configuration is correct, you can test it with `kafka-avro-console-consumer`, `kafka-avro-console-producer` available
in Confluent Schema Registry distribution. After you'll get properly working set of properties, you just need to copy it to Nussknacker's configuration.

#### Azure-based Schema Registry - Connection and Authentication

For Azure-based implementation, firstly you should provide `schema.registry.url` and `schema.group` properties. First one should be the
`https://<event-hubs-namespace>.servicebus.windows.net` url, the second one should be the name of schema groups where will be
located all schemas used by Nussknacker.

Regarding authentication, a couple of options can be used - you can provide credential via:
`azure.tenant.id`, `azure.client.id` and `azure.client.secret` properties, or you can use one of other methods handled
by Azure's [DefaultAzureCredential](https://learn.microsoft.com/en-us/java/api/overview/azure/identity-readme?view=azure-java-stable#defaultazurecredential).
For example via Azure CLI or Azure PowerShell.

Integration with Azure Schema Registry requires Kafka connection, make sure you have provided proper [configuration](#kafka---authentication).

## Messaging

You can use standard kafka-cli commands like `kafka-console-consumer`, `kafka-console-producer`, [kcat](https://github.com/edenhill/kcat), Confluent's
`kafka-avro-console-consumer`, `kafka-avro-console-producer` commands for Confluent-based Avro encoded messages or graphical tools like [AKHQ](https://akhq.io)
to interact with kafka source and sink topics used in Nu scenarios.

Be aware that Azure-based Avro encoded messages have a little different format than Confluent - Schema ID is passed in headers instead of payload.
It can be less supported by some available tools. See [Schema Registry comparison section](#schema-registry-comparison) for details.

### Message Payload

By default, Nussknacker supports two combinations of schema type and payload type:

* Avro schema + Avro payload (binary format)
* JSON schema + JSON payload (human readable, text format)

Avro payloads are more concise, because messages contain only values and schema id - without information about message structure like field names.

Avro payload is compatible with standard Kafka serializers and deserializers delivered by Schema Registry providers. Thanks to that you should be able to send messages to Nussknacker and read messages
produced by Nussknacker using standard tooling available around those Schema Registries. To see how those formats are different, take a look at [Schema Registry comparison section](#schema-registry-comparison)

For some situations it might be helpful to use JSON payload with Avro schema. Especially when your Schema Registry doesn't support JSON schemas.
You can do that by enabling `avroAsJsonSerialization` configuration setting.

### Schema ID

Each topic can contain messages written using different schema versions. Schema versions are identified by *Schema ID*. 
Nussknacker needs to know what was the schema used during writing to make message validation and schema evolution possible.
Because of that Nussknacker needs to extract *Schema ID* from the message.

Additionally, in sources and sinks, you can choose which schema version should be used during reading/writing. Thanks to schema evolution mechanism, message in the original format will be evolved to desired format.
This desired schema will be used in [code completion and validation](../integration/DataTypingAndSchemasHandling.md).

At runtime Nussknacker determines the schema version of a message value and key in the following way:
1. It checks in `key.schemaId`, `value.schemaId` and Azure-specific `content-type` headers;
2. If no such headers provided, it looks for the magic byte (0x00) and a schema id in the message, [in a format used by Confluent](https://docs.confluent.io/platform/current/schema-registry/serdes-develop/index.html#wire-format);
3. If the magic byte is not found, it assumes the schema version chosen by the user in the scenario.

### Schema Registry comparison

Below you can find a quick comparison of how given schema registry types are handled:

| Schema registry type | What is used for association<br/>between schema with topic | Convention                                          | The way how schema id is passed in message                                                                        | Payload content                       |
|----------------------|------------------------------------------------------------|-----------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|---------------------------------------|
| Confluent            | Subjects                                                   | Subject = `<topic-name>-(key or value)`             | For Avro: in payload in format: 0x00, 4 bytes schema id, Avro payload                                             | 0x00, 4 bytes schema id, Avro payload |
|                      |                                                            |                                                     | For JSON: in  `key.schemaId` or `value.schemaId` headers                                                          | JSON payload                          |
| Azure                | Schema names                                               | Schema name = `<CamelCasedTopicName>(Key or Value)` | For Avro: In header: content-type: avro/binary+schemaId                                                           | Avro payload                          |
|                      |                                                            |                                                     | For JSON: in  `key.schemaId` or `value.schemaId` headers<br/>(only when `avroAsJsonSerialization` option enabled) | JSON payload                          |

## Configuration details

### Common part
The Kafka configuration is part of the Model configuration. All the settings below should be placed relative to `scenarioTypes.ScenarioTypeName.modelConfig` key. You can find the high level structure of the configuration file [here](../configuration/#configuration-areas)

Both streaming Engines (Lite and Flink) share some common Kafka settings this section describes them, see respective sections below for details on configuring Kafka for particular Engine (e.g. the keys where the common settings should be placed at).

### Available configuration options

Important thing to remember is that Kafka server addresses/Schema Registry addresses have to be resolvable from:
- Nussknacker Designer host (to enable schema discovery and scenario testing)
- Lite/Flink engine - to be able to run job

| Name                                                                        | Importance | Type     | Default value | Description                                                                                                                                                                                                                                                                                                                              |
|-----------------------------------------------------------------------------|------------|----------|---------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| kafkaProperties."bootstrap.servers"                                         | High       | string   |               | Comma separated list of [bootstrap servers](https://kafka.apache.org/documentation/#producerconfigs_bootstrap.servers)                                                                                                                                                                                                                   |
| kafkaProperties."schema.registry.url"                                       | High       | string   |               | Comma separated list of schema registry urls                                                                                                                                                                                                                                                                                             |
| kafkaProperties."basic.auth.credentials.source"                             | High       | string   |               | (Confluent-only) Source of credential e.g. `USER_INFO`                                                                                                                                                                                                                                                                                   |
| kafkaProperties."basic.auth.user.info"                                      | High       | string   |               | (Confluent-only) User and password e.g. `some_user:some_password`                                                                                                                                                                                                                                                                        |
| kafkaProperties."schema.group"                                              | High       | string   |               | (Azure-only) Schema group with all available schemas                                                                                                                                                                                                                                                                                     |
| kafkaProperties."azure.tenant.id"                                           | High       | string   |               | (Azure-only) Azure's tenant id                                                                                                                                                                                                                                                                                                           |
| kafkaProperties."azure.client.id"                                           | High       | string   |               | (Azure-only) Azure's client id                                                                                                                                                                                                                                                                                                           |
| kafkaProperties."azure.client.secret"                                       | High       | string   |               | (Azure-only) Azure's client secret                                                                                                                                                                                                                                                                                                       |
| kafkaProperties."transaction.timeout.ms"                                    | Medium     | number   | 600000        | Transaction timeout in millis for transactional producer [transaction timeout](https://kafka.apache.org/documentation/#producerconfigs_transaction.timeout.ms)                                                                                                                                                                           |
| kafkaProperties."isolation.level"                                           | High       | string   |               | Controls how to read messages written transactionally. [isolation.level](https://kafka.apache.org/documentation/#consumerconfigs_isolation.level)                                                                                                                                                                                        |
| kafkaProperties                                                             | Medium     | map      |               | Additional configuration of [producers](https://kafka.apache.org/documentation/#producerconfigs) or [consumers](https://kafka.apache.org/documentation/#consumerconfigs)                                                                                                                                                                 |
| useStringForKey                                                             | Medium     | boolean  | true          | Kafka message keys will be in the string format (not in Avro)                                                                                                                                                                                                                                                                            |
| kafkaEspProperties.forceLatestRead                                          | Medium     | boolean  | false         | If scenario is restarted, should offsets of source consumers be reset to latest (can be useful in test enrivonments)                                                                                                                                                                                                                     |
| topicsExistenceValidationConfig.enabled                                     | Low        | boolean  | true          | Determine if existence of topics should be validated. For Kafka topics used in Sinks, topic existence won't be checked if [auto.create.topics.enable](https://kafka.apache.org/documentation/#brokerconfigs_auto.create.topics.enable) is true on Kafka cluster - note, that it may require permissions to access Kafka cluster settings |
| topicsExistenceValidationConfig.validatorConfig.autoCreateFlagFetchCacheTtl | Low        | duration | 5 minutes     | TTL for checking Kafka cluster settings                                                                                                                                                                                                                                                                                                  |
| topicsExistenceValidationConfig.validatorConfig.topicsFetchCacheTtl         | Low        | duration | 30 seconds    | TTL for caching list of existing topics                                                                                                                                                                                                                                                                                                  |
| topicsExistenceValidationConfig.validatorConfig.adminClientTimeout          | Low        | duration | 10 seconds    | Timeout for communicating with Kafka cluster                                                                                                                                                                                                                                                                                             |
| schemaRegistryCacheConfig.availableSchemasExpirationTime                    | Low        | duration | 10 seconds    | How often available schemas cache will be invalidated. This determines the maximum time you'll have to wait after adding new schema or new schema version until it will be available in Designer                                                                                                                                         |
| schemaRegistryCacheConfig.parsedSchemaAccessExpirationTime                  | Low        | duration | 2 hours       | How long parsed schema will be cached after first access to it                                                                                                                                                                                                                                                                           |
| schemaRegistryCacheConfig.maximumSize                                       | Low        | number   | 10000         | Maximum entries size for each caches: available schemas cache and parsed schema cache                                                                                                                                                                                                                                                    |
| avroAsJsonSerialization                                                     | Low        | boolean  | false         | Send and receive json messages described using Avro schema                                                                                                                                                                                                                                                                               |

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

Following properties can be configured (please look at correct engine page : [Lite](../configuration/model/Lite#exception-handling) or [Flink](../configuration/model/Flink#configuring-exception-handling), 
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

See [common config](#available-configuration-options) for the details of Kafka configuration, the table below presents additional options available only in Flink engine:


| Name                                              | Importance | Type                                     | Default value      | Description                                                                                                                                                                                                     |
|---------------------------------------------------|------------|------------------------------------------|--------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| kafkaEspProperties.defaultMaxOutOfOrdernessMillis | Medium     | long                                     | 60000 (60 seconds) | Configuration of [bounded out of orderness watermark generator](https://ci.apache.org/projects/flink/flink-docs-stable/docs/dev/datastream/event-time/built_in/#fixed-amount-of-lateness) used by Kafka sources |
| idleTimeout.enabled                               | Medium     | boolean                                  | true               | Enabling [idleness](https://nightlies.apache.org/flink/flink-docs-stable/docs/connectors/datastream/kafka/#idleness) used by Kafka sources                                                                      |
| idleTimeout.duration                              | Medium     | long                                     | 3 minutes          | Configuration of [idle timout](https://nightlies.apache.org/flink/flink-docs-stable/docs/connectors/datastream/kafka/#idleness) used by Kafka sources                                                           |
| consumerGroupNamingStrategy                       | Low        | processId/processId-nodeId               | processId-nodeId   | How consumer groups for sources should be named                                                                                                                                                                 |
| avroKryoGenericRecordSchemaIdSerialization        | Low        | boolean                                  | true               | Should Avro messages from topics registered in schema registry be serialized in optimized way, by serializing only schema id, not the whole schema                                                              |
| sinkDeliveryGuarantee                             | Medium     | enum (EXACTLY_ONCE, AT_LEAST_ONCE, NONE) | AT_LEAST_ONCE      | Configuration of [fault tolerance semantic](https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/connectors/datastream/kafka/#fault-tolerance)                                                       |
            

### Configuration for Lite engine

The Lite engine in Streaming processing mode uses Kafka as it's core part (e.g. delivery guarantees are based on Kafka transactions), so it's configured separately from other components. Therefore, it's only possible to use one Kafka cluster for one model configuration. This configuration is used for all
Kafka based sources and sinks (you don't need to configure them separately). See [common config](#available-configuration-options) for the details.
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
