package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.universal

import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.avro.RuntimeSchemaData
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.ConfluentKafkaAvroSerializer
import pl.touk.nussknacker.engine.avro.serialization.KafkaSchemaBasedValueSerializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.KafkaConfig

trait ConfluentUniversalSerializerFactory {

  protected def createSerializer[T](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                    kafkaConfig: KafkaConfig,
                                    schemaOpt: Option[RuntimeSchemaData[ParsedSchema]],
                                    isKey: Boolean): Serializer[T] = {
    val schemaRegistryClient = schemaRegistryClientFactory.create(kafkaConfig)

    val serializer = schemaOpt.map(_.schema) match {
      case Some(schema: AvroSchema) => ConfluentKafkaAvroSerializer(kafkaConfig, schemaRegistryClient, Some(schema), isKey = isKey)
      //todo: add support for json schema
      case Some(other) => throw new IllegalArgumentException(s"Not supported schema type: ${other.schemaType()}")
      case None => throw new IllegalArgumentException("SchemaData should be defined for universal serializer")
    }

    serializer.asInstanceOf[Serializer[T]]
  }
}

class ConfluentUniversalKafkaSerializationSchemaFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaSchemaBasedValueSerializationSchemaFactory with ConfluentUniversalSerializerFactory {

  override protected def createValueSerializer(schemaOpt: Option[RuntimeSchemaData[ParsedSchema]], kafkaConfig: KafkaConfig): Serializer[Any] =
    createSerializer[Any](schemaRegistryClientFactory, kafkaConfig, schemaOpt, isKey = false)
}
