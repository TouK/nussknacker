package pl.touk.nussknacker.engine.avro.schemaregistry

import cats.data.ValidatedNel
import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.avro.Schema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.avro.UniversalRuntimeSchemaData
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.serialization.{KafkaAvroDeserializationSchemaFactory, KafkaAvroSerializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.serialization.{KafkaDeserializationSchema, KafkaSerializationSchema}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, RecordFormatter, RecordFormatterFactory, serialization}
import pl.touk.nussknacker.engine.util.KeyedValue

import scala.reflect.ClassTag

trait SchemaRegistryProvider extends Serializable with BaseSchemaRegistryProvider {

  def deserializationSchemaFactory: KafkaAvroDeserializationSchemaFactory

  def serializationSchemaFactory: KafkaAvroSerializationSchemaFactory

  def recordFormatterFactory: RecordFormatterFactory

  def validateSchema(schema: Schema): ValidatedNel[SchemaRegistryError, Schema]
}


trait KafkaSerDeSchemaRegistryProvider extends Serializable with BaseSchemaRegistryProvider {

  val kafkaConfig: KafkaConfig

  def createKafkaDeserializationSchema[K: ClassTag, V: ClassTag](
                                                                  keySchemaDataOpt: Option[UniversalRuntimeSchemaData],
                                                                  valueSchemaDataOpt: Option[UniversalRuntimeSchemaData]
                                                                ): KafkaDeserializationSchema[ConsumerRecord[K, V]]

  def createKafkaSerializationSchema(topic: String, version: Option[Int], schema: UniversalRuntimeSchemaData): serialization.KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]]

  def recordFormatter[K: ClassTag, V: ClassTag](sourceDeserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]]): RecordFormatter

  def validateSchema(schema: ParsedSchema): ValidatedNel[SchemaRegistryError, ParsedSchema]
}