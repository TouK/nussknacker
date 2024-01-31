package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.schema.{AvroSchemaEvolution, DefaultAvroSchemaEvolution}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.ConfluentSchemaRegistryClient

import java.util
import scala.jdk.CollectionConverters._

/**
  * This is Kafka Avro Serializer class. All events will be serialized to provided schema.
  */
class ConfluentKafkaAvroSerializer(
    kafkaConfig: KafkaConfig,
    confluentSchemaRegistryClient: ConfluentSchemaRegistryClient,
    schemaEvolutionHandler: AvroSchemaEvolution,
    avroSchemaOpt: Option[AvroSchema],
    _isKey: Boolean
) extends AbstractConfluentKafkaAvroSerializer(schemaEvolutionHandler)
    with Serializer[Any] {

  schemaRegistry = confluentSchemaRegistryClient.client

  configure(kafkaConfig.kafkaProperties.getOrElse(Map.empty).asJava, _isKey)

  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {
    val avroConfig = new KafkaAvroSerializerConfig(configs)
    configureClientProperties(avroConfig, ConfluentUtils.SchemaProvider)
    this.autoRegisterSchema = avroConfig.autoRegisterSchema
    this.isKey = isKey
  }

  override def serialize(topic: String, data: Any): Array[Byte] = {
    serialize(topic, null, data)
  }

  override def serialize(topic: String, headers: Headers, data: Any): Array[Byte] = {
    serialize(avroSchemaOpt, topic, data, isKey, headers)
  }

  // It is a work-around for two different close() methods (one throws IOException and another not) in AbstractKafkaSchemaSerDe and in Serializer
  // It is needed only for scala 2.12
  override def close(): Unit = {}
}

object ConfluentKafkaAvroSerializer {

  def apply(
      kafkaConfig: KafkaConfig,
      schemaRegistryClient: ConfluentSchemaRegistryClient,
      avroSchemaOpt: Option[AvroSchema],
      isKey: Boolean
  ): ConfluentKafkaAvroSerializer = {
    new ConfluentKafkaAvroSerializer(
      kafkaConfig,
      schemaRegistryClient,
      new DefaultAvroSchemaEvolution,
      avroSchemaOpt,
      _isKey = isKey
    )
  }

}
