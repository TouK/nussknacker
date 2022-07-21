package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.universal

import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.avro.{AvroUtils, RuntimeSchemaData}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.ConfluentKafkaAvroDeserializer
import pl.touk.nussknacker.engine.avro.serialization.KafkaSchemaBasedKeyValueDeserializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.KafkaConfig

import scala.reflect.ClassTag

trait ConfluentUniversalKafkaDeserializerFactory extends LazyLogging {

  protected def createDeserializer[T: ClassTag](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                                kafkaConfig: KafkaConfig,
                                                schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
                                                isKey: Boolean): Deserializer[T] = {
    val schemaRegistryClient = schemaRegistryClientFactory.create(kafkaConfig)
    val schemaData = schemaDataOpt.getOrElse(throw new IllegalStateException("SchemaData should be defined for universal deserializer"))
    schemaData.schema match {
      case _: AvroSchema =>
        val avroSchemaData = schemaData.asInstanceOf[RuntimeSchemaData[AvroSchema]]
        new ConfluentKafkaAvroDeserializer[T](kafkaConfig, Some(avroSchemaData), schemaRegistryClient, _isKey = isKey, AvroUtils.isSpecificRecord[T])
      // TODO: handle json payload for json schema and json payload for avro schema
      case other => throw new IllegalArgumentException(s"Unsupported schema class: ${other.getClass}")
    }
  }

}

class ConfluentKeyValueUniversalKafkaDeserializationFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaSchemaBasedKeyValueDeserializationSchemaFactory with ConfluentUniversalKafkaDeserializerFactory {

  override protected def createKeyDeserializer[K: ClassTag](schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]], kafkaConfig: KafkaConfig): Deserializer[K] =
    createDeserializer[K](schemaRegistryClientFactory, kafkaConfig, schemaDataOpt, isKey = true)

  override protected def createValueDeserializer[V: ClassTag](schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]], kafkaConfig: KafkaConfig): Deserializer[V] =
    createDeserializer[V](schemaRegistryClientFactory, kafkaConfig, schemaDataOpt, isKey = false)

}