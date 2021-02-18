package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.jsonpayload

import java.lang

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.avro.{AvroUtils, RuntimeSchemaData}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.{ConfluentKafkaAvroDeserializer, ConfluentKafkaAvroDeserializerFactory}
import pl.touk.nussknacker.engine.avro.serialization.KafkaAvroValueDeserializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import tech.allegro.schema.json2avro.converter.JsonAvroConverter

import scala.reflect.ClassTag

class ConfluentJsonPayloadDeserializerFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaAvroValueDeserializationSchemaFactory {

  override protected def createValueDeserializer[T: ClassTag](schemaDataOpt: Option[RuntimeSchemaData],
                                                              kafkaConfig: KafkaConfig): Deserializer[T] = {
    val schemaRegistryClient = schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)
    new ConfluentKafkaAvroDeserializer[T](kafkaConfig, schemaDataOpt.orNull,
      schemaRegistryClient, isKey = false, AvroUtils.isSpecificRecord[T]) {

      val converter = new JsonAvroConverter();

      override protected def deserialize(topic: String, isKey: lang.Boolean, payload: Array[Byte], readerSchema: RuntimeSchemaData): AnyRef = {
        converter.convertToGenericDataRecord(payload, readerSchema.schema)
      }
    }
  }

  override protected def createValueTypeInfo[T: ClassTag](schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig): TypeInformation[T] =
    ConfluentUtils.typeInfoForSchema[T](kafkaConfig, schemaDataOpt)

}