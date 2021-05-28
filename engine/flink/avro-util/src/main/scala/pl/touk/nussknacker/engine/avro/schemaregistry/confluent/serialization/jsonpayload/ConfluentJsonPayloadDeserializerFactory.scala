package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.jsonpayload

import org.apache.avro.specific.SpecificRecordBase
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.ConfluentKafkaAvroDeserializer
import pl.touk.nussknacker.engine.avro.serialization.KafkaAvroValueDeserializationSchemaFactory
import pl.touk.nussknacker.engine.avro.{AvroUtils, RuntimeSchemaData}
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import tech.allegro.schema.json2avro.converter.JsonAvroConverter

import java.lang
import scala.reflect.ClassTag

class ConfluentJsonPayloadDeserializerFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaAvroValueDeserializationSchemaFactory {

  override protected def createValueDeserializer[T: ClassTag](schemaDataOpt: Option[RuntimeSchemaData],
                                                              kafkaConfig: KafkaConfig): Deserializer[T] = {
    val schemaRegistryClient = schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)

    val specificClass = {
      val clazz = implicitly[ClassTag[T]].runtimeClass
      //This is a bit tricky, Allegro decoder requires SpecificRecordBase instead of SpecificRecord
      if (classOf[SpecificRecordBase].isAssignableFrom(clazz)) Some(clazz.asInstanceOf[Class[SpecificRecordBase]]) else None
    }

    new ConfluentKafkaAvroDeserializer[T](kafkaConfig, schemaDataOpt,
      schemaRegistryClient, isKey = false, specificClass.isDefined) {

      private val converter = new JsonAvroConverter();

      override protected def deserialize(topic: String, isKey: lang.Boolean, payload: Array[Byte], readerSchema: Option[RuntimeSchemaData]): AnyRef = {
        val schema = readerSchema.get.schema
        specificClass match {
          case Some(kl) => converter.convertToSpecificRecord(payload, kl, schema)
          case None => converter.convertToGenericDataRecord(payload, schema)
        }
      }
    }
  }

  override protected def createValueTypeInfo[T: ClassTag](schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig): TypeInformation[T] =
    ConfluentUtils.typeInfoForSchema[T](kafkaConfig, schemaDataOpt)

}

