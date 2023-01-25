package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization

import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.schemedkafka.schema.{AvroSchemaEvolution, DefaultAvroSchemaEvolution}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.ConfluentSchemaRegistryClient
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.serialization.SchemaRegistryBasedSerializerFactory

import java.util
import scala.jdk.CollectionConverters._

/**
  * This is Kafka Avro Serializer class. All events will be serialized to provided schema.
  */
class ConfluentKafkaAvroSerializer(kafkaConfig: KafkaConfig, confluentSchemaRegistryClient: ConfluentSchemaRegistryClient, schemaEvolutionHandler: AvroSchemaEvolution,
                                   avroSchemaOpt: Option[AvroSchema], var isKey: Boolean)
  extends AbstractConfluentKafkaAvroSerializer(schemaEvolutionHandler) with Serializer[Any] {

  schemaRegistry = confluentSchemaRegistryClient.client

  configure(kafkaConfig.kafkaProperties.getOrElse(Map.empty).asJava, isKey)

  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {
    val avroConfig = new KafkaAvroSerializerConfig(configs)
    configureClientProperties(avroConfig, ConfluentUtils.SchemaProvider)
    this.autoRegisterSchema = avroConfig.autoRegisterSchema
    this.isKey = isKey
  }

  override def serialize(topic: String, data: Any): Array[Byte] =
    serialize(avroSchemaOpt, topic, data, isKey)

  override def close(): Unit = {}
}

object ConfluentKafkaAvroSerializer {
  def apply(kafkaConfig: KafkaConfig, schemaRegistryClient: SchemaRegistryClient, avroSchemaOpt: Option[AvroSchema], isKey: Boolean): ConfluentKafkaAvroSerializer = {
    new ConfluentKafkaAvroSerializer(kafkaConfig, schemaRegistryClient.asInstanceOf[ConfluentSchemaRegistryClient], new DefaultAvroSchemaEvolution, avroSchemaOpt, isKey = isKey)
  }
}

object ConfluentAvroSerializerFactory extends SchemaRegistryBasedSerializerFactory {

  def createSerializer(schemaRegistryClient: SchemaRegistryClient,
                       kafkaConfig: KafkaConfig,
                       schemaOpt: Option[RuntimeSchemaData[ParsedSchema]],
                       isKey: Boolean): Serializer[Any] = {
    val avroSchemaOpt = schemaOpt.map(_.schema).map {
      case schema: AvroSchema => schema
      case schema => throw new IllegalArgumentException(s"Not supported schema type: ${schema.schemaType()}")
    }
    ConfluentKafkaAvroSerializer(kafkaConfig, schemaRegistryClient, avroSchemaOpt, isKey = isKey)
  }

}