package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import java.util
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.avro.schema.{AvroSchemaEvolution, DefaultAvroSchemaEvolution}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClient
import pl.touk.nussknacker.engine.avro.typed.AvroSettings
import pl.touk.nussknacker.engine.kafka.KafkaConfig

import scala.collection.JavaConverters._

/**
  * This is Kafka Avro Serializer class. All events will be serialized to provided schema.
  */
class ConfluentKafkaAvroSerializer(kafkaConfig: KafkaConfig, confluentSchemaRegistryClient: ConfluentSchemaRegistryClient, schemaEvolutionHandler: AvroSchemaEvolution,
                                   avroSchemaOpt: Option[AvroSchema], var isKey: Boolean, avroSettings: AvroSettings)
  extends AbstractConfluentKafkaAvroSerializer(schemaEvolutionHandler, avroSettings) with Serializer[Any] {

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
  def apply(kafkaConfig: KafkaConfig, confluentSchemaRegistryClient: ConfluentSchemaRegistryClient, avroSchemaOpt: Option[AvroSchema], isKey: Boolean,
            avroSettings: AvroSettings): ConfluentKafkaAvroSerializer = {
    new ConfluentKafkaAvroSerializer(kafkaConfig, confluentSchemaRegistryClient, new DefaultAvroSchemaEvolution(avroSettings),
      avroSchemaOpt, isKey = isKey, avroSettings = avroSettings)
  }
}
