# Description

This module adds Avro support. It uses Confluent Schema Registry for storing Avro schemas. It basically provides:
`AvroDeserializationSchemaFactory` and `AvroSerializationSchemaFactory` which can be used with `KafkaSourceFactory`.
It also provides extension for `KafkaSourceFactory` (`KafkaAvroSourceFactory`) which store test data in JSON format.

# TODOs

Some features that would be nice to have:
* typed Avro factory
  * ability to fetch Avro schema from Schema Registry
* resources consumption
  * closing of `SchemaRegistryClient`
  * sharing `SchemaRegistryClient` between sources if it is created from the same configuration
