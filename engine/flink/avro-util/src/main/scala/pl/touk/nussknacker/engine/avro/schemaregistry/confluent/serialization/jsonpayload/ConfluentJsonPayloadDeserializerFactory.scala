package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.jsonpayload

import org.apache.avro.specific.SpecificRecordBase
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.ConfluentKafkaAvroDeserializer
import pl.touk.nussknacker.engine.avro.serialization.{KafkaAvroKeyValueDeserializationSchemaFactory, KafkaAvroValueDeserializationSchemaFactory}
import pl.touk.nussknacker.engine.avro.RuntimeSchemaData
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import tech.allegro.schema.json2avro.converter.JsonAvroConverter
import java.lang

import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.reflect.{ClassTag, classTag}


trait ConfluentJsonPayloadDeserializer {

  protected def createDeserializer[T: ClassTag](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                                kafkaConfig: KafkaConfig,
                                                schemaDataOpt: Option[RuntimeSchemaData],
                                                isKey: Boolean): Deserializer[T] = {
    val schemaRegistryClient = schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)

    val specificClass = {
      val clazz = implicitly[ClassTag[T]].runtimeClass
      //This is a bit tricky, Allegro decoder requires SpecificRecordBase instead of SpecificRecord
      if (classOf[SpecificRecordBase].isAssignableFrom(clazz)) Some(clazz.asInstanceOf[Class[SpecificRecordBase]]) else None
    }

    new ConfluentKafkaAvroDeserializer[T](kafkaConfig, schemaDataOpt.orNull,
      schemaRegistryClient, isKey = false, specificClass.isDefined) {

      private val converter = new JsonAvroConverter();

      override protected def deserialize(topic: String, isKey: lang.Boolean, payload: Array[Byte], readerSchema: RuntimeSchemaData): AnyRef = {
        val schema = readerSchema.schema
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

class ConfluentJsonPayloadDeserializerFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaAvroValueDeserializationSchemaFactory with ConfluentJsonPayloadDeserializer {

  override protected def createValueDeserializer[T: ClassTag](schemaDataOpt: Option[RuntimeSchemaData],
                                                              kafkaConfig: KafkaConfig): Deserializer[T] =
    createDeserializer[T](schemaRegistryClientFactory, kafkaConfig, schemaDataOpt, isKey = false)

  override protected def createValueTypeInfo[T: ClassTag](schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig): TypeInformation[T] =
    createTypeInfo[T](kafkaConfig, schemaDataOpt)

}

class ConfluentConsumerRecordJsonPayloadDeserializerFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaAvroKeyValueDeserializationSchemaFactory with ConfluentJsonPayloadDeserializer {

  override protected type K = Any
  override protected type V = Any
  override protected type O = ConsumerRecord[Any, Any]

  override protected def createKeyDeserializer(schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig, keyClassTagOpt: Option[ClassTag[_]]): Deserializer[K] =
    schemaDataOpt
      .map(schema => createDeserializer[K](schemaRegistryClientFactory, kafkaConfig, Some(schema), isKey = true)(keyClassTagOpt.getOrElse(classTag[Any]).asInstanceOf[ClassTag[K]]))
      .getOrElse(KafkaAvroKeyValueDeserializationSchemaFactory.defaultKeyAsStringDeserializer.asInstanceOf[Deserializer[K]])

  override protected def createKeyTypeInfo(schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig, keyClassTagOpt: Option[ClassTag[_]]): TypeInformation[K] =
    schemaDataOpt
      .map(schema => createTypeInfo[K](kafkaConfig, Some(schema))(keyClassTagOpt.getOrElse(classTag[Any]).asInstanceOf[ClassTag[K]]))
      .getOrElse(KafkaAvroKeyValueDeserializationSchemaFactory.defaultKeyAsStringTypeInformation.asInstanceOf[TypeInformation[K]])

  override protected def createValueDeserializer(schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig, valueClassTagOpt: Option[ClassTag[_]]): Deserializer[V] =
    createDeserializer[V](schemaRegistryClientFactory, kafkaConfig, schemaDataOpt, isKey = false)(valueClassTagOpt.getOrElse(classTag[Any]).asInstanceOf[ClassTag[K]])

  override protected def createValueTypeInfo(schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig, valueClassTagOpt: Option[ClassTag[_]]): TypeInformation[V] =
    createTypeInfo[V](kafkaConfig, schemaDataOpt)(valueClassTagOpt.getOrElse(classTag[Any]).asInstanceOf[ClassTag[K]])

  override protected def createObject(record: ConsumerRecord[K, V]): O = record.asInstanceOf[ConsumerRecord[Any, Any]]

  override protected def createObjectTypeInformation(keyTypeInformation: TypeInformation[K], valueTypeInformation: TypeInformation[V]): TypeInformation[O] = {
    TypeInformation.of(classOf[O])
  }
}