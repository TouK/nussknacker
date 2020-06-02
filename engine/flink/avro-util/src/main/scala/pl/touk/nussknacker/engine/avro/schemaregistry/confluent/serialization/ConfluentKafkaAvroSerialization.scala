package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import org.apache.avro.Schema
import org.apache.kafka.common.errors.SerializationException
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClient

private[serialization] trait ConfluentKafkaAvroSerialization {

  protected def schemaByTopicAndVersion(confluentSchemaRegistry: ConfluentSchemaRegistryClient, topic: String, version: Option[Int], isKey: Boolean): Schema = {
    val subject = AvroUtils.topicSubject(topic, isKey = isKey)
    confluentSchemaRegistry
      .getFreshSchema(subject, version)
      .valueOr(exc => throw new SerializationException(s"Error retrieving Avro schema for topic $topic.", exc))
  }
}
