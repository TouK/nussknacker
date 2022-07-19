package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.jsonpayload

import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.specific.SpecificRecordBase
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.avro.RuntimeSchemaData
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.ConfluentKafkaAvroDeserializer
import pl.touk.nussknacker.engine.avro.serialization.KafkaSchemaBasedKeyValueDeserializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.KafkaConfig

import java.lang
import scala.reflect.ClassTag

/**
  * ConfluentJsonPayloadDeserializer converts json payload to avro.
  * Used with schemas WITHOUT logical types due to avro conversion limitations.
  *
  * It uses avro schema which has impact on json format requirements:
  * 1. Avro schema is used to deserialize json payload to GenericRecord or SpecificRecord.
  *    Values should be encoded with avro conversions (see example below).
  * 2. Allegro json2avro decoder is used instead of confluent decoder.
  *    Decoded json should have flattened object structure (see example below).
  *
  * TODO: Add support to json payloads specified in json schema
  *
  * Example object with logical types (serialized with BestJsonEffortEncoder, see [GeneratedAvroClassWithLogicalTypes]):
  * {
  *   "text" : "lorem ipsum",
  *   "dateTime" : "2020-01-02T03:14:15Z",
  *   "date" : "2020-01-02",
  *   "time" : "03:14:15"
  * }
  *
  * Example of json readable by confluent decoder (see [ConfluentAvroMessageFormatter]):
  * {
  *   "text" : "lorem ipsum",
  *   "dateTime" : {
  *     "long" : 1577934855000
  *   },
  *   "date" : {
  *     "int" : 18263
  *   },
  *   "time" : {
  *     "int" : 11655000
  *   }
  * }
  *
  * The same example to be used with allegro json2avro decoder:
  * {
  *   "text" : "lorem ipsum",
  *   "dateTime" : 1577934855000,
  *   "date" : 18263,
  *   "time" : 11655000
  * }
  */
trait ConfluentJsonPayloadDeserializer {

  protected def createDeserializer[T: ClassTag](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                                kafkaConfig: KafkaConfig,
                                                schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
                                                isKey: Boolean): Deserializer[T] = {
    val schemaRegistryClient = schemaRegistryClientFactory.create(kafkaConfig)

    val specificClass = {
      val clazz = implicitly[ClassTag[T]].runtimeClass
      //This is a bit tricky, Allegro decoder requires SpecificRecordBase instead of SpecificRecord
      if (classOf[SpecificRecordBase].isAssignableFrom(clazz)) Some(clazz.asInstanceOf[Class[SpecificRecordBase]]) else None
    }

    val avroSchemaDataOpt = schemaDataOpt.map { schemaData =>
      schemaData.schema match {
        case _: AvroSchema => schemaData.asInstanceOf[RuntimeSchemaData[AvroSchema]]
        case other => throw new IllegalArgumentException(s"Unsupported schema class: ${other.getClass}")
      }
    }

    new ConfluentKafkaAvroDeserializer[T](kafkaConfig, avroSchemaDataOpt, schemaRegistryClient, isKey, specificClass.isDefined) {

      private val converter = new JsonPayloadToAvroConverter(specificClass)

      override protected def deserialize(topic: String, isKey: lang.Boolean, payload: Array[Byte], readerSchema: Option[RuntimeSchemaData[AvroSchema]]): AnyRef = {
        val schema = readerSchema.get.schema.rawSchema()
        converter.convert(payload, schema)
      }
    }
  }

}

class ConfluentKeyValueKafkaJsonDeserializerFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaSchemaBasedKeyValueDeserializationSchemaFactory with ConfluentJsonPayloadDeserializer {

  override protected def createKeyDeserializer[K: ClassTag](schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]], kafkaConfig: KafkaConfig): Deserializer[K] =
    createDeserializer[K](schemaRegistryClientFactory, kafkaConfig, schemaDataOpt, isKey = true)

  override protected def createValueDeserializer[V: ClassTag](schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]], kafkaConfig: KafkaConfig): Deserializer[V] =
    createDeserializer[V](schemaRegistryClientFactory, kafkaConfig, schemaDataOpt, isKey = false)

}
