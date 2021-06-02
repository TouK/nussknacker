package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.jsonpayload

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.Schema
import org.apache.avro.io.{Encoder, NoWrappingJsonEncoder}
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.avro.schema.{AvroSchemaEvolution, DefaultAvroSchemaEvolution}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{ConfluentSchemaRegistryClient, ConfluentSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.ConfluentKafkaAvroSerializer
import pl.touk.nussknacker.engine.avro.serialization.KafkaAvroValueSerializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.KafkaConfig

import java.io.OutputStream

//TODO: handle situation, where we have both json and avro payloads for one schema registry
class ConfluentJsonPayloadSerializerFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaAvroValueSerializationSchemaFactory {

  override protected def createValueSerializer(schemaOpt: Option[Schema], version: Option[Int], kafkaConfig: KafkaConfig): Serializer[Any] = {

    val schemaRegistryClient = schemaRegistryClientFactory.create(kafkaConfig)
    val avroSchemaOpt = schemaOpt.map(ConfluentUtils.convertToAvroSchema(_, version))

    new JsonPayloadKafkaSerializer(kafkaConfig, schemaRegistryClient, new DefaultAvroSchemaEvolution, avroSchemaOpt, isKey = false)
  }
}

class JsonPayloadKafkaSerializer(kafkaConfig: KafkaConfig,
                                 confluentSchemaRegistryClient: ConfluentSchemaRegistryClient,
                                 schemaEvolutionHandler: AvroSchemaEvolution,
                                 avroSchemaOpt: Option[AvroSchema], isKey: Boolean) extends ConfluentKafkaAvroSerializer(kafkaConfig, confluentSchemaRegistryClient, schemaEvolutionHandler, avroSchemaOpt, isKey = false) {

  override protected def encoderToUse(schema: Schema, out: OutputStream): Encoder = new NoWrappingJsonEncoder(schema, out)

  override protected def writeHeader(data: Any, avroSchema: Schema, schemaId: Int, out: OutputStream): Unit = {}

}
