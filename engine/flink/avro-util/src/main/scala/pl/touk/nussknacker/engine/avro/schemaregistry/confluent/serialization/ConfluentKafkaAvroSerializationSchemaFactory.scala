package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.avro.AvroSchemaDeterminer
import pl.touk.nussknacker.engine.avro.schemaregistry.BasedOnVersionAvroSchemaDeterminer
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.serialization.{KafkaVersionAwareKeyValueSerializationSchemaFactory, KafkaVersionAwareValueSerializationSchemaFactory}

trait ConfluentAvroSerializerFactory {

  protected def createSerializer[T](schemaDeterminer: AvroSchemaDeterminer,
                                    schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                    topic: String,
                                    version: Option[Int],
                                    kafkaConfig: KafkaConfig,
                                    isKey: Boolean): Serializer[T] = {
    val schemaRegistryClient = schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)

    val schema = schemaDeterminer.determineSchemaInRuntime
      .valueOr(exc => throw new SerializationException(s"Error determining Avro schema.", exc))
      .map(ConfluentUtils.convertToAvroSchema(_, version))

    val serializer = ConfluentKafkaAvroSerializer(kafkaConfig, schemaRegistryClient, schema, isKey = isKey)
    serializer.asInstanceOf[Serializer[T]]
  }
}

class ConfluentAvroSerializationSchemaFactory(createSchemaDeterminer: (String, Option[Int]) => AvroSchemaDeterminer,
                                              schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaVersionAwareValueSerializationSchemaFactory[AnyRef] with ConfluentAvroSerializerFactory {

  override protected def createValueSerializer(topic: String, version: Option[Int], kafkaConfig: KafkaConfig): Serializer[AnyRef] =
    createSerializer[AnyRef](createSchemaDeterminer(topic, version), schemaRegistryClientFactory, topic, version, kafkaConfig, isKey = false)
}

object ConfluentAvroSerializationSchemaFactory {
  def apply(kafkaConfig: KafkaConfig, schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory): ConfluentAvroSerializationSchemaFactory =
    new ConfluentAvroSerializationSchemaFactory(new BasedOnVersionAvroSchemaDeterminer(() => schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig), _, _), schemaRegistryClientFactory)
}

abstract class ConfluentAvroKeyValueSerializationSchemaFactory(createSchemaDeterminer: (String, Option[Int]) => AvroSchemaDeterminer,
                                                               schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaVersionAwareKeyValueSerializationSchemaFactory[AnyRef] with ConfluentAvroSerializerFactory {

  override protected def createKeySerializer(topic: String, version: Option[Int], kafkaConfig: KafkaConfig): Serializer[K] =
    createSerializer[K](createSchemaDeterminer(topic, version), schemaRegistryClientFactory, topic, version, kafkaConfig, isKey = true)

  override protected def createValueSerializer(topic: String, version: Option[Int], kafkaConfig: KafkaConfig): Serializer[V] =
    createSerializer[V](createSchemaDeterminer(topic, version), schemaRegistryClientFactory, topic, version, kafkaConfig, isKey = false)
}
