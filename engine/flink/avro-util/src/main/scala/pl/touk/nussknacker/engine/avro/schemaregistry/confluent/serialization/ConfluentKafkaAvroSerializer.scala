package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import java.util

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.avro.schema.{AvroSchemaEvolution, DefaultAvroSchemaEvolution}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClient
import pl.touk.nussknacker.engine.kafka.KafkaConfig

import scala.collection.JavaConverters._

/**
  * This is Kafka Avro Serializer class. All events will be serialized to provided schema.
  */
class ConfluentKafkaAvroSerializer(kafkaConfig: KafkaConfig, confluentSchemaRegistryClient: ConfluentSchemaRegistryClient, schemaEvolutionHandler: AvroSchemaEvolution,
                                   schemaOpt: Option[AvroSchema], var isKey: Boolean)
  extends AbstractConfluentKafkaAvroSerializer(schemaEvolutionHandler) with Serializer[AnyRef] {

  schemaRegistry = confluentSchemaRegistryClient.client

  configure(kafkaConfig.kafkaProperties.getOrElse(Map.empty).asJava, isKey)

  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {
    val avroConfig = new KafkaAvroSerializerConfig(configs)
    configureClientProperties(avroConfig, ConfluentUtils.SchemaProvider)
    this.autoRegisterSchema = avroConfig.autoRegisterSchema
    this.isKey = isKey
  }

  override def serialize(topic: String, data: AnyRef): Array[Byte] =
    serialize(schemaOpt, topic, data, isKey)

  override def close(): Unit = {}
}

object ConfluentKafkaAvroSerializer {
  def apply(kafkaConfig: KafkaConfig, confluentSchemaRegistryClient: ConfluentSchemaRegistryClient, schemaOpt: Option[AvroSchema], isKey: Boolean): ConfluentKafkaAvroSerializer = {
    new ConfluentKafkaAvroSerializer(kafkaConfig, confluentSchemaRegistryClient, new DefaultAvroSchemaEvolution, schemaOpt, isKey = isKey)
  }
}
