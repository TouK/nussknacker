package pl.touk.nussknacker.engine.avro.source

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import pl.touk.nussknacker.engine.avro.helpers.{KafkaAvroSpecMixin, SimpleKafkaJsonSerializer}
import pl.touk.nussknacker.engine.avro.schema.FullNameV1
import pl.touk.nussknacker.engine.avro.schemaregistry.ExistingSchemaVersion
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory

class KafkaJsonPayloadSourceFactorySpec extends KafkaAvroSpecMixin with KafkaAvroSourceSpecMixin {

  import KafkaAvroSourceMockSchemaRegistry._

  override protected def schemaRegistryClient: SchemaRegistryClient = schemaRegistryMockClient

  override protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory = factory

  override protected lazy val schemaRegistryProvider: ConfluentSchemaRegistryProvider = ConfluentSchemaRegistryProvider.jsonPayload(confluentClientFactory)

  override protected def valueSerializer: SimpleKafkaJsonSerializer.type = SimpleKafkaJsonSerializer

  test("should read generated record in v1") {
    val givenObj = FullNameV1.exampleData

    roundTripValueObject(avroSourceFactory(useStringForKey = true), RecordTopic, ExistingSchemaVersion(1), "", FullNameV1.record)
  }
}
