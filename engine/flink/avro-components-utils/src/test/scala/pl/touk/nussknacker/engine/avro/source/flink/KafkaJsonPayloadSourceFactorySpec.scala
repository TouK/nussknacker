package pl.touk.nussknacker.engine.avro.source.flink

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.avro.helpers.{KafkaAvroSpecMixin, SimpleKafkaJsonSerializer}
import pl.touk.nussknacker.engine.avro.schema.{FullNameV1, FullNameV2, GeneratedAvroClassSample, GeneratedAvroClassSampleSchema}
import pl.touk.nussknacker.engine.avro.schemaregistry.ExistingSchemaVersion
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaBasedSerdeProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.source.SpecificRecordKafkaAvroSourceFactory
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.source.flink.FlinkKafkaSourceImplFactory

class KafkaJsonPayloadSourceFactorySpec extends KafkaAvroSpecMixin with KafkaAvroSourceSpecMixin {
  import KafkaAvroSourceMockSchemaRegistry._

  override protected def schemaRegistryClient: CSchemaRegistryClient = schemaRegistryMockClient

  override protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory = factory

  // Use kafka-json serializers
  override protected def keySerializer: Serializer[Any] = SimpleKafkaJsonSerializer

  override protected def valueSerializer: Serializer[Any] = SimpleKafkaJsonSerializer

  override def resolveConfig(config: Config): Config = super.resolveConfig(config).withValue("kafka.avroAsJsonSerialization", fromAnyRef(true))

  ignore("should read generated generic record in v1 with null key") {
    val givenValue = FullNameV1.record

    roundTripKeyValueObject(universalSourceFactory, useStringForKey = true, RecordTopic, ExistingSchemaVersion(1), null, givenValue)
  }

  test("should read generated generic record in v2 (latest) with null key") {
    val givenValue = FullNameV2.record

    roundTripKeyValueObject(universalSourceFactory, useStringForKey = true, RecordTopic, ExistingSchemaVersion(2), null, givenValue)
  }

  //todo
  ignore("should read generated generic record in v1 with empty string key") {
    val givenValue = FullNameV1.record

    roundTripKeyValueObject(universalSourceFactory, useStringForKey = true, RecordTopic, ExistingSchemaVersion(1), "", givenValue)
  }

  test("should read generated generic record in v2 (latest) with empty string key") {
    val givenValue = FullNameV2.record

    roundTripKeyValueObject(universalSourceFactory, useStringForKey = true, RecordTopic, ExistingSchemaVersion(2), "", givenValue)
  }

  test("should read generated specific record in v1") {
    val givenValue = GeneratedAvroClassSampleSchema.specificRecord.asInstanceOf[GeneratedAvroClassSample]

    val factory = (useStringForKey: Boolean) => new SpecificRecordKafkaAvroSourceFactory[GeneratedAvroClassSample](confluentClientFactory, ConfluentSchemaBasedSerdeProvider.jsonPayload(confluentClientFactory), testProcessObjectDependencies, new FlinkKafkaSourceImplFactory(None)) {
      override protected def prepareKafkaConfig: KafkaConfig = super.prepareKafkaConfig.copy(useStringForKey = useStringForKey)
    }.asInstanceOf[KafkaSource]

    roundTripKeyValueObject(factory, useStringForKey = true, RecordTopic, ExistingSchemaVersion(1), "", givenValue)
  }
}
