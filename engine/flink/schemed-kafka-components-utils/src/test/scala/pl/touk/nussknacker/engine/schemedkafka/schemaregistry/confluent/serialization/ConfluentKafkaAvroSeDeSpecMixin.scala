package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.{Deserializer, Serializer}
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.flink.util.keyed.StringKeyedValue
import pl.touk.nussknacker.engine.schemedkafka.TestSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.helpers._
import pl.touk.nussknacker.engine.schemedkafka.schema.FullNameV1
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaBasedSerdeProvider
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentSchemaBasedSerdeProvider
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.{CachedConfluentSchemaRegistryClientFactory, MockConfluentSchemaRegistryClientBuilder}
import pl.touk.nussknacker.engine.kafka.{KafkaClient, serialization}
import pl.touk.nussknacker.engine.util.KeyedValue

trait ConfluentKafkaAvroSeDeSpecMixin extends SchemaRegistryMixin with TableDrivenPropertyChecks {

  object MockSchemaRegistry {
    final val fullNameTopic = "full-name"

    val schemaRegistryMockClient: SchemaRegistryClient = new MockConfluentSchemaRegistryClientBuilder()
      .register(fullNameTopic, FullNameV1.schema, 1, isKey = false)
      .build

    val factory: CachedConfluentSchemaRegistryClientFactory = TestSchemaRegistryClientFactory(schemaRegistryMockClient)
  }

  lazy val avroSetup: SchemaRegistryProviderSetup = SchemaRegistryProviderSetup(SchemaRegistryProviderSetupType.avro,
        ConfluentSchemaBasedSerdeProvider.avroPayload(MockSchemaRegistry.factory),
        new SimpleKafkaAvroSerializer(MockSchemaRegistry.schemaRegistryMockClient, isKey = false),
        new SimpleKafkaAvroDeserializer(MockSchemaRegistry.schemaRegistryMockClient, _useSpecificAvroReader = false))

  lazy val jsonSetup: SchemaRegistryProviderSetup = SchemaRegistryProviderSetup(SchemaRegistryProviderSetupType.json,
        ConfluentSchemaBasedSerdeProvider.jsonPayload(MockSchemaRegistry.factory),
        SimpleKafkaJsonSerializer,
        SimpleKafkaJsonDeserializer)

  object SchemaRegistryProviderSetupType extends Enumeration {
    val json, avro = Value
  }

  case class SchemaRegistryProviderSetup(`type`: SchemaRegistryProviderSetupType.Value,
                                         provider: SchemaBasedSerdeProvider,
                                         override val valueSerializer: Serializer[Any],
                                         valueDeserializer: Deserializer[Any]) extends KafkaWithSchemaRegistryOperations {

    override protected def prepareValueDeserializer(useSpecificAvroReader: Boolean): Deserializer[Any] = valueDeserializer

    override protected def schemaRegistryClient: SchemaRegistryClient = ConfluentKafkaAvroSeDeSpecMixin.this.schemaRegistryClient

    override protected def kafkaClient: KafkaClient = ConfluentKafkaAvroSeDeSpecMixin.this.kafkaClient

    def pushMessage(kafkaSerializer: serialization.KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]], obj: AnyRef, topic: String): RecordMetadata = {
      val record = kafkaSerializer.serialize(StringKeyedValue(null, obj), Predef.Long2long(null))
      kafkaClient.sendRawMessage(topic, record.key(), record.value(), headers = record.headers()).futureValue
    }
  }

}
