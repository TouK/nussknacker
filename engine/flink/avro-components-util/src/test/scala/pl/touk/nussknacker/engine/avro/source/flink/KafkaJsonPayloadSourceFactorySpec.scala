package pl.touk.nussknacker.engine.avro.source.flink

import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.avro.helpers.{KafkaAvroSpecMixin, SimpleKafkaJsonSerializer}
import pl.touk.nussknacker.engine.avro.schema.{FullNameV1, GeneratedAvroClassSample, GeneratedAvroClassSampleSchema}
import pl.touk.nussknacker.engine.avro.schemaregistry.ExistingSchemaVersion
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory

class KafkaJsonPayloadSourceFactorySpec extends KafkaAvroSpecMixin with KafkaAvroSourceSpecMixin {
  import KafkaAvroSourceMockSchemaRegistry._

  override protected def schemaRegistryClient: CSchemaRegistryClient = schemaRegistryMockClient

  override protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory = factory

  // Use SchemaRegistryProvider for jsonPayload
  override protected lazy val schemaRegistryProvider: ConfluentSchemaRegistryProvider = ConfluentSchemaRegistryProvider.jsonPayload(confluentClientFactory)

  // Use kafka-json serializers
  override protected def keySerializer: Serializer[Any] = SimpleKafkaJsonSerializer

  override protected def valueSerializer: Serializer[Any] = SimpleKafkaJsonSerializer

  test("should read generated generic record in v1 with null key") {
    val givenValue = FullNameV1.record

    roundTripKeyValueObject(avroSourceFactory, useStringForKey = true, RecordTopic, ExistingSchemaVersion(1), null, givenValue)
  }

  test("should read generated generic record in v1 with empty string key") {
    val givenValue = FullNameV1.record

    roundTripKeyValueObject(avroSourceFactory, useStringForKey = true, RecordTopic, ExistingSchemaVersion(1), "", givenValue)
  }

  test("should read generated specific record in v1") {
    val givenValue = GeneratedAvroClassSampleSchema.specificRecord.asInstanceOf[GeneratedAvroClassSample]

    roundTripKeyValueObject(specificSourceFactory[GeneratedAvroClassSample], useStringForKey = true, RecordTopic, ExistingSchemaVersion(1), "", givenValue)
  }

}
