package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.kafka.common.serialization.{Deserializer, Serializer}
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.avro.TestSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.helpers._
import pl.touk.nussknacker.engine.avro.schema.FullNameV1
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{CachedConfluentSchemaRegistryClientFactory, MockConfluentSchemaRegistryClientBuilder}
import pl.touk.nussknacker.engine.kafka.KafkaClient

trait ConfluentKafkaAvroSeDeSpecMixin extends SchemaRegistryMixin with TableDrivenPropertyChecks {

  object MockSchemaRegistry {
    final val fullNameTopic = "full-name"

    val schemaRegistryMockClient: SchemaRegistryClient = new MockConfluentSchemaRegistryClientBuilder()
      .register(fullNameTopic, FullNameV1.schema, 1, isKey = false)
      .build

    val factory: CachedConfluentSchemaRegistryClientFactory = TestSchemaRegistryClientFactory(schemaRegistryMockClient)
  }

  lazy val avroSetup: SchemaRegistryProviderSetup = SchemaRegistryProviderSetup(SchemaRegistryProviderSetupType.avro,
        ConfluentSchemaRegistryProvider.avroPayload(MockSchemaRegistry.factory),
        new SimpleKafkaAvroSerializer(MockSchemaRegistry.schemaRegistryMockClient, isKey = false),
        new SimpleKafkaAvroDeserializer(MockSchemaRegistry.schemaRegistryMockClient, _useSpecificAvroReader = false))

  lazy val jsonSetup: SchemaRegistryProviderSetup = SchemaRegistryProviderSetup(SchemaRegistryProviderSetupType.json,
        ConfluentSchemaRegistryProvider.jsonPayload(MockSchemaRegistry.factory),
        SimpleKafkaJsonSerializer,
        SimpleKafkaJsonDeserializer)

  object SchemaRegistryProviderSetupType extends Enumeration {
    val json, avro = Value
  }

  case class SchemaRegistryProviderSetup(`type`: SchemaRegistryProviderSetupType.Value,
                                         provider: SchemaRegistryProvider,
                                         override val valueSerializer: Serializer[Any],
                                         valueDeserializer: Deserializer[Any]) extends KafkaWithSchemaRegistryOperations {

    override protected def prepareValueDeserializer(useSpecificAvroReader: Boolean): Deserializer[Any] = valueDeserializer

    override protected def schemaRegistryClient: SchemaRegistryClient = ConfluentKafkaAvroSeDeSpecMixin.this.schemaRegistryClient

    override protected def kafkaClient: KafkaClient = ConfluentKafkaAvroSeDeSpecMixin.this.kafkaClient

  }

}
