package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.kafka.KafkaComponentsConfig
import pl.touk.nussknacker.engine.schemedkafka.schema.{AvroSchemaEvolution, DefaultAvroSchemaEvolution}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.ConfluentSchemaRegistryClient

import java.util
import scala.jdk.CollectionConverters._

/**
  * This is Kafka Avro Serializer class. All events will be serialized to provided schema.
  */
class ConfluentKafkaAvroSerializer(
    kafkaComponentsConfig: KafkaComponentsConfig,
    confluentSchemaRegistryClient: ConfluentSchemaRegistryClient,
    schemaEvolutionHandler: AvroSchemaEvolution,
    avroSchemaOpt: Option[AvroSchema],
    _isKey: Boolean
) extends AbstractConfluentKafkaAvroSerializer(schemaEvolutionHandler)
    with Serializer[Any] {

  schemaRegistry = confluentSchemaRegistryClient.client

  configure(kafkaComponentsConfig.kafkaProperties.asJava, _isKey)

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

}

object ConfluentKafkaAvroSerializer {

  def apply(
      kafkaComponentsConfig: KafkaComponentsConfig,
      schemaRegistryClient: ConfluentSchemaRegistryClient,
      avroSchemaOpt: Option[AvroSchema],
      isKey: Boolean
  ): ConfluentKafkaAvroSerializer = {
    new ConfluentKafkaAvroSerializer(
      kafkaComponentsConfig,
      schemaRegistryClient,
      new DefaultAvroSchemaEvolution,
      avroSchemaOpt,
      _isKey = isKey
    )
  }

}
