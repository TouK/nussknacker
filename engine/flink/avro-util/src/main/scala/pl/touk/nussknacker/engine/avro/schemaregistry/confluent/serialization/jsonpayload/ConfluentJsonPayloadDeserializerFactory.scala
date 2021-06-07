package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.jsonpayload

import java.lang

import org.apache.avro.specific.SpecificRecordBase
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.avro.RuntimeSchemaData
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.ConfluentKafkaAvroDeserializer
import pl.touk.nussknacker.engine.avro.serialization.KafkaAvroKeyValueDeserializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import tech.allegro.schema.json2avro.converter.JsonAvroConverter

import scala.reflect.ClassTag

trait ConfluentJsonPayloadDeserializer {

  protected def createDeserializer[T: ClassTag](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                                kafkaConfig: KafkaConfig,
                                                schemaDataOpt: Option[RuntimeSchemaData],
                                                isKey: Boolean): Deserializer[T] = {
    val schemaRegistryClient = schemaRegistryClientFactory.create(kafkaConfig)

    val specificClass = {
      val clazz = implicitly[ClassTag[T]].runtimeClass
      //This is a bit tricky, Allegro decoder requires SpecificRecordBase instead of SpecificRecord
      if (classOf[SpecificRecordBase].isAssignableFrom(clazz)) Some(clazz.asInstanceOf[Class[SpecificRecordBase]]) else None
    }

    new ConfluentKafkaAvroDeserializer[T](kafkaConfig, schemaDataOpt, schemaRegistryClient, isKey, specificClass.isDefined) {

      private val converter = new JsonAvroConverter()

      override protected def deserialize(topic: String, isKey: lang.Boolean, payload: Array[Byte], readerSchema: Option[RuntimeSchemaData]): AnyRef = {
        val schema = readerSchema.get.schema
        specificClass match {
          case Some(kl) => converter.convertToSpecificRecord(payload, kl, schema)
          case None => converter.convertToGenericDataRecord(payload, schema)
        }
      }
    }
  }

  protected def createTypeInfo[T: ClassTag](kafkaConfig: KafkaConfig, schemaDataOpt: Option[RuntimeSchemaData]): TypeInformation[T] = {
    ConfluentUtils.typeInfoForSchema[T](kafkaConfig, schemaDataOpt)
  }

}

class ConfluentKeyValueKafkaJsonDeserializerFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaAvroKeyValueDeserializationSchemaFactory with ConfluentJsonPayloadDeserializer {

  override protected def createKeyDeserializer[K: ClassTag](schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig): Deserializer[K] =
    createDeserializer[K](schemaRegistryClientFactory, kafkaConfig, schemaDataOpt, isKey = true)

  override protected def createKeyTypeInfo[K: ClassTag](schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig): TypeInformation[K] =
    createTypeInfo[K](kafkaConfig, schemaDataOpt)

  override protected def createValueDeserializer[V: ClassTag](schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig): Deserializer[V] =
    createDeserializer[V](schemaRegistryClientFactory, kafkaConfig, schemaDataOpt, isKey = false)

  override protected def createValueTypeInfo[V: ClassTag](schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig): TypeInformation[V] =
    createTypeInfo[V](kafkaConfig, schemaDataOpt)

}