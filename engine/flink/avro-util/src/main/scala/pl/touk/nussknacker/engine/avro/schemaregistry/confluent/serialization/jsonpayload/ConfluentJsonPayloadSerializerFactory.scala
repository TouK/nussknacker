package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.jsonpayload

import java.util

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDe
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.avro.schema.DefaultAvroSchemaEvolution
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.ConfluentKafkaAvroSerializer
import pl.touk.nussknacker.engine.avro.serialization.KafkaAvroValueSerializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import tech.allegro.schema.json2avro.converter.JsonAvroConverter

class ConfluentJsonPayloadSerializerFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaAvroValueSerializationSchemaFactory {

  override protected def createValueSerializer(schemaOpt: Option[Schema], version: Option[Int], kafkaConfig: KafkaConfig): Serializer[AnyRef] = {

    val schemaRegistryClient = schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)

    val avroSchemaOpt = schemaOpt.map(ConfluentUtils.convertToAvroSchema(_, version))

    new ConfluentKafkaAvroSerializer(kafkaConfig, schemaRegistryClient, new DefaultAvroSchemaEvolution,
      avroSchemaOpt, isKey = false) {

      val converter = new JsonAvroConverter()

      override protected def writeData(data: Any, avroSchema: Schema, schemaId: Int): Array[Byte] = {
        //FIXME: jakos ladniej??
        data match {
          case genericRecord: GenericRecord =>
            converter.convertToJson(genericRecord)
          case other =>
            val normal = super.writeData(other, avroSchema, schemaId)
            val withoutSchemaId = util.Arrays.copyOfRange(normal, 1 + AbstractKafkaSchemaSerDe.idSize, normal.length)
            converter.convertToJson(withoutSchemaId, avroSchema)
        }
      }
    }.asInstanceOf[Serializer[AnyRef]]

  }
}
