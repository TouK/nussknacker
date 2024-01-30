package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization.jsonpayload

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.Schema
import org.apache.avro.io.{Encoder, NoWrappingJsonEncoder}
import org.apache.kafka.common.header.Headers
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.schema.AvroSchemaEvolution
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaId
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.ConfluentSchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization.ConfluentKafkaAvroSerializer

import java.io.OutputStream

//TODO: handle situation, where we have both json and avro payloads for one schema registry
class ConfluentJsonPayloadKafkaSerializer(
    kafkaConfig: KafkaConfig,
    confluentSchemaRegistryClient: ConfluentSchemaRegistryClient,
    schemaEvolutionHandler: AvroSchemaEvolution,
    avroSchemaOpt: Option[AvroSchema],
    isKey: Boolean
) extends ConfluentKafkaAvroSerializer(
      kafkaConfig,
      confluentSchemaRegistryClient,
      schemaEvolutionHandler,
      avroSchemaOpt,
      _isKey = isKey
    ) {

  override protected def encoderToUse(schema: Schema, out: OutputStream): Encoder =
    new NoWrappingJsonEncoder(schema, out)

  override protected def writeHeader(schemaId: SchemaId, out: OutputStream, headers: Headers): Unit = {}

}
