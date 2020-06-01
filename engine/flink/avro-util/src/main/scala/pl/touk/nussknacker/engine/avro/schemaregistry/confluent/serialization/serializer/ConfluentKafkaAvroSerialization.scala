package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.serializer

import java.nio.ByteBuffer

import org.apache.avro.Schema
import org.apache.kafka.common.errors.SerializationException
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClient

private [serializer] trait ConfluentKafkaAvroSerialization {

  protected def schemaByTopicAndVersion(confluentSchemaRegistry: ConfluentSchemaRegistryClient, topic: String, version: Option[Int], isKey: Boolean): Schema = {
    val subject = AvroUtils.topicSubject(topic, isKey = isKey)
    confluentSchemaRegistry
      .getFreshSchema(subject, version)
      .valueOr(exc => throw new SerializationException(s"Error retrieving Avro schema for topic $topic.", exc))
  }

  protected def parsePayloadToByteBuffer(payload: Array[Byte]): ByteBuffer =
    ConfluentUtils
      .parsePayloadToByteBuffer(payload)
      .valueOr(exc => throw new SerializationException(exc.getMessage, exc))
}
