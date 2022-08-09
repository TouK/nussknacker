package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization.jsonpayload

import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.Schema
import org.apache.avro.io.{Encoder, NoWrappingJsonEncoder}
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.schema.{AvroSchemaEvolution, DefaultAvroSchemaEvolution}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.{ConfluentSchemaRegistryClient, ConfluentSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization.ConfluentKafkaAvroSerializer
import pl.touk.nussknacker.engine.schemedkafka.serialization.KafkaSchemaBasedValueSerializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.KafkaConfig

import java.io.OutputStream

//TODO: handle situation, where we have both json and avro payloads for one schema registry
class ConfluentJsonPayloadSerializerFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaSchemaBasedValueSerializationSchemaFactory {

  override protected def createValueSerializer(schemaOpt: Option[RuntimeSchemaData[ParsedSchema]], kafkaConfig: KafkaConfig): Serializer[Any] = {

    val schemaRegistryClient = schemaRegistryClientFactory.create(kafkaConfig)

    val avroSchemaOpt = schemaOpt.map(_.schema).map {
      case schema: AvroSchema => schema
      case schema => throw new IllegalArgumentException(s"Not supported schema type: ${schema.schemaType()}")
    }

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
