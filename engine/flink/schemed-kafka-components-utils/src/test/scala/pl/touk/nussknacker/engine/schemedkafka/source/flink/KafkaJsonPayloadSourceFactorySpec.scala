package pl.touk.nussknacker.engine.schemedkafka.source.flink

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.schemedkafka.helpers.{KafkaAvroSpecMixin, SimpleKafkaJsonSerializer}
import pl.touk.nussknacker.engine.schemedkafka.schema.{FullNameV1, FullNameV2}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{ExistingSchemaVersion, SchemaRegistryClientFactory}

class KafkaJsonPayloadSourceFactorySpec extends KafkaAvroSpecMixin with KafkaAvroSourceSpecMixin {
  import KafkaAvroSourceMockSchemaRegistry._

  override protected def schemaRegistryClient: CSchemaRegistryClient = schemaRegistryMockClient

  override protected def schemaRegistryClientFactory: SchemaRegistryClientFactory = factory

  override protected def keySerializer: Serializer[Any] = SimpleKafkaJsonSerializer

  override protected def valueSerializer: Serializer[Any] = SimpleKafkaJsonSerializer

  override def resolveConfig(config: Config): Config =
    super.resolveConfig(config).withValue("kafka.avroAsJsonSerialization", fromAnyRef(true))

  test("should read generated generic record in v1 with null key") {
    val givenValue = FullNameV1.record

    roundTripKeyValueObject(
      universalSourceFactory,
      useStringForKey = true,
      RecordTopic.name,
      ExistingSchemaVersion(1),
      null,
      givenValue
    )
  }

  test("should read generated generic record in v2 (latest) with null key") {
    val givenValue = FullNameV2.record

    roundTripKeyValueObject(
      universalSourceFactory,
      useStringForKey = true,
      RecordTopic.name,
      ExistingSchemaVersion(2),
      null,
      givenValue
    )
  }

  test("should read generated generic record in v1 with empty string key") {
    val givenValue = FullNameV1.record

    roundTripKeyValueObject(
      universalSourceFactory,
      useStringForKey = true,
      RecordTopic.name,
      ExistingSchemaVersion(1),
      "",
      givenValue
    )
  }

  test("should read generated generic record in v2 (latest) with empty string key") {
    val givenValue = FullNameV2.record

    roundTripKeyValueObject(
      universalSourceFactory,
      useStringForKey = true,
      RecordTopic.name,
      ExistingSchemaVersion(2),
      "",
      givenValue
    )
  }

}
