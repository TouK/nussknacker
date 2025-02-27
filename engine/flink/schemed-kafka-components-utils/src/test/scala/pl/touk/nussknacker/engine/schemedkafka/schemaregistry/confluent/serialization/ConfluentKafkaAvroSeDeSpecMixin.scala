package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.{Deserializer, Serializer}
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.flink.util.keyed.StringKeyedValue
import pl.touk.nussknacker.engine.kafka.{serialization, KafkaClient}
import pl.touk.nussknacker.engine.schemedkafka.helpers._
import pl.touk.nussknacker.engine.schemedkafka.schema.FullNameV1
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaBasedSerdeProvider, SchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.MockConfluentSchemaRegistryClientBuilder
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.MockSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.util.KeyedValue

trait ConfluentKafkaAvroSeDeSpecMixin extends SchemaRegistryMixin with TableDrivenPropertyChecks {

  object MockSchemaRegistry {
    final val fullNameTopic = "full-name"

    val schemaRegistryMockClient: SchemaRegistryClient = new MockConfluentSchemaRegistryClientBuilder()
      .register(fullNameTopic, FullNameV1.schema, 1, isKey = false)
      .build

    val factory: SchemaRegistryClientFactory = MockSchemaRegistryClientFactory.confluentBased(schemaRegistryMockClient)
  }

  object SchemaRegistryProviderSetupType extends Enumeration {
    val avro = Value
  }

  case class SchemaRegistryProviderSetup(
      `type`: SchemaRegistryProviderSetupType.Value,
      provider: SchemaBasedSerdeProvider,
      override val valueSerializer: Serializer[Any],
      valueDeserializer: Deserializer[Any]
  ) extends KafkaWithSchemaRegistryOperations {

    override protected def prepareValueDeserializer: Deserializer[Any] =
      valueDeserializer

    override protected def schemaRegistryClient: SchemaRegistryClient =
      ConfluentKafkaAvroSeDeSpecMixin.this.schemaRegistryClient

    override protected def kafkaClient: KafkaClient = ConfluentKafkaAvroSeDeSpecMixin.this.kafkaClient

    def pushMessage(
        kafkaSerializer: serialization.KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]],
        obj: AnyRef,
        topic: String
    ): RecordMetadata = {
      val record = kafkaSerializer.serialize(StringKeyedValue(null, obj), Predef.Long2long(null))
      kafkaClient.sendRawMessage(topic, record.key(), record.value(), headers = record.headers()).futureValue
    }

  }

}
