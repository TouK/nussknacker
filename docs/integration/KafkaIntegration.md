---
sidebar_position: 1
---

# Kafka

## Prerequisites

To fully understand how Nussknacker works with Kafka topics, it's best to read the following first:
* [Role of Schema Registry](/about/TypicalImplementation)
* [Confluent Schema Registry](https://docs.confluent.io/platform/current/schema-registry/index.html)
* [Kafka topic key](https://kafka.apache.org/intro)

If you want to use Streaming-Flink processing engine, this is also recommended:
* [Flink keyed state](https://ci.apache.org/projects/flink/flink-docs-master/docs/concepts/stateful-stream-processing/#keyed-state)

## Sources and sinks

Kafka topics are native streaming data input to Nussknacker and the native output where results of Nussknacker scenarios processing are placed. In Nussknacker terminology input topics are called sources, output topics are called sinks. This section provides important details of Nussknacker's integration with Kafka and schema registry.

## Schema registry integration

Nussknacker integrates with a schema registry. It is the source of topics available to choose from in sources and sinks. It also allows Nussknacker to provide syntax suggestions and validation. Nussknacker assumes that for the topic `topic-name` a schema `topic-name-value` and optionally `topic-name-key` (for the Kafka topic key) will be defined in the schema registry.

Schemas are stored and managed by Confluent Schema Registry; it is [bundled with Nussknacker](/about/TypicalImplementation) in all deployment versions. Schemas can be registered in Schema Registry by means of REST API based CLI or using AKHQ, an open source GUI for Apache Kafka and Confluent Schema Registry. AKHQ is bundled with Nussknacker in all deployment versions.

Nussknacker supports both JSON and AVRO schemas, and JSON and AVRO topic payloads. Detailed information on how AVRO data should be serialized/deserialized can be found in [Confluent Wire Documentation](https://docs.confluent.io/platform/current/schema-registry/serdes-develop/index.html#wire-format).

## Schema and payload types

By default Nussknacker supports two combinations of schema type and payload type:

* AVRO schema + AVRO payload
* JSON schema + JSON payload

If you prefer using JSON payload with AVRO schema, you can use `avroAsJsonSerialization` configuration setting to change that behaviour ([see Configuration for details](/docs/installation_configuration_guide/ModelConfiguration#common-kafka-configuration)).

## Schema ID

Nussknacker supports schema evolution.

In the Designer the user can choose, for each source and sink, which schema version should be used for syntax suggestions and validation.

At runtime Nussknacker determines the schema version of a message value and key in the following way:
1. it checks `value.schemaId` and `key.schemaId` headers;
2. if there are no such headers, it looks for a magic byte and schema version in the message, [in a format used by Confluent](https://docs.confluent.io/platform/current/schema-registry/serdes-develop/index.html#wire-format);
3. if the magic byte is not found, it assumes the schema version chosen by the user in the scenario.