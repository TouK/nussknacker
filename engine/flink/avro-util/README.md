# Description

This module adds Avro support. It uses Confluent Schema Registry for storing avro schemas. It basically provides:
`AvroDeserializationSchemaFactory` and `AvroSerializationSchemaFactory` which can be used with `KafkaSourceFactory`.
It also provides extension for `KafkaSourceFactory` (`KafkaAvroSourceFactory`) which store test data in json format.

# TODOs

Some features that would be nice to have:
* typed avro factory
  * similar to *kafka-typed-json* support of type definition (see *generic* module)
  * ability to use avro schema in type definition instead of custom one
  * ability to fetch avro schema from Schema Registry
* resources consumption
  * closing of `SchemaRegistryClient`
  * sharing `SchemaRegistryClient` between sources if it is created from the same configuration
* testing
  * better handling formatting of test data - for now, in keys in test data there can't be a pipe character