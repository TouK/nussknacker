package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization.jsonpayload

import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.Schema
import org.apache.avro.io.{Encoder, NoWrappingJsonEncoder}
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.schema.{AvroSchemaEvolution, DefaultAvroSchemaEvolution}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.ConfluentSchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization.ConfluentKafkaAvroSerializer
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.serialization.SchemaRegistryBasedSerializerFactory

import java.io.OutputStream

//TODO: handle situation, where we have both json and avro payloads for one schema registry
class ConfluentJsonPayloadKafkaSerializer(kafkaConfig: KafkaConfig,
                                          confluentSchemaRegistryClient: SchemaRegistryClient,
                                          schemaEvolutionHandler: AvroSchemaEvolution,
                                          avroSchemaOpt: Option[AvroSchema], isKey: Boolean)
  extends ConfluentKafkaAvroSerializer(kafkaConfig, confluentSchemaRegistryClient.asInstanceOf[ConfluentSchemaRegistryClient], schemaEvolutionHandler, avroSchemaOpt, isKey = false) {

  override protected def encoderToUse(schema: Schema, out: OutputStream): Encoder = new NoWrappingJsonEncoder(schema, out)

  override protected def writeHeader(data: Any, avroSchema: Schema, schemaId: Int, out: OutputStream): Unit = {}

}

object ConfluentJsonPayloadSerializerFactory extends SchemaRegistryBasedSerializerFactory {
  override def createSerializer(schemaRegistryClient: SchemaRegistryClient,
                                kafkaConfig: KafkaConfig,
                                schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
                                isKey: Boolean): Serializer[Any] = {
    val avroSchemaOpt = schemaDataOpt.map(_.schema).map {
      case schema: AvroSchema => schema
      case schema => throw new IllegalArgumentException(s"Not supported schema type: ${schema.schemaType()}")
    }
    new ConfluentJsonPayloadKafkaSerializer(kafkaConfig, schemaRegistryClient, new DefaultAvroSchemaEvolution, avroSchemaOpt, isKey = false)
  }

}