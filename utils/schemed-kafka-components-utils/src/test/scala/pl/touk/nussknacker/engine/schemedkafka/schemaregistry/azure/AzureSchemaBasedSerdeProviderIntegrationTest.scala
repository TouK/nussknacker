package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure

import org.apache.avro.generic.{GenericRecordBuilder, IndexedRecord}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.record.TimestampType
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.tags.Network
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.kafka.KafkaComponentsConfig
import pl.touk.nussknacker.engine.schemedkafka.{AvroUtils, RuntimeSchemaData}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.{
  UniversalSchemaBasedSerdeProvider,
  UniversalSchemaRegistryClientFactory
}
import pl.touk.nussknacker.engine.util.KeyedValue

import java.nio.charset.StandardCharsets
import java.util.Optional

@Network
class AzureSchemaBasedSerdeProviderIntegrationTest extends AnyFunSuite with OptionValues with Matchers {

  test("serialization round-trip") {
    val eventHubsNamespace = Option(System.getenv("AZURE_EVENT_HUBS_NAMESPACE")).getOrElse("nu-cloud")
    val config = Map(
      "bootstrap.servers"     -> "dummy:9092",
      "schema.registry.url"   -> s"https://$eventHubsNamespace.servicebus.windows.net",
      "schema.group"          -> "test-group",
      "auto.register.schemas" -> "true",
    )
    val kafkaComponentsConfig = KafkaComponentsConfig(
      config,
      None,
      showTopicsWithoutSchema = false,
    )
    val schema = AvroUtils.parseSchema("""{
        |    "type": "record",
        |    "namespace": "pl.touk.nussknacker",
        |    "name": "test_schema",
        |    "fields": [
        |        {
        |            "name": "a",
        |            "type": "string"
        |        }
        |    ]
        |}""".stripMargin)

    val record = new GenericRecordBuilder(schema)
      .set("a", "aValue")
      .build()
    val key = "sample-key"
    val serdeProvider =
      UniversalSchemaBasedSerdeProvider.create(UniversalSchemaRegistryClientFactory, kafkaComponentsConfig)
    val valueSchemaData = RuntimeSchemaData(schema, None).toParsedSchemaData
    val pr = serdeProvider.serializationSchemaFactory
      // TODO: we should check if schema name matches our topic-schema name matching convention in the serializer
      .create(TopicName.ForSink("notImportantTopicNme"), valueSchemaData)
      .serialize(new KeyedValue[AnyRef, AnyRef](key, record), timestamp = 0L)

    val contentTypeHeader      = Option(pr.headers().lastHeader("content-type")).value
    val contentTypeHeaderValue = new String(contentTypeHeader.value(), StandardCharsets.UTF_8)
    contentTypeHeaderValue should startWith("avro/binary+")

    val cr = new ConsumerRecord[Array[Byte], Array[Byte]](
      pr.topic(),
      pr.partition(),
      0L,
      pr.timestamp(),
      TimestampType.CREATE_TIME,
      pr.key().length,
      pr.value().length,
      pr.key(),
      pr.value(),
      pr.headers(),
      Optional.empty[Integer]()
    )
    val deserialized = serdeProvider.deserializationSchemaFactory
      .create[String, IndexedRecord](keySchemaDataOpt = None, Some(valueSchemaData), dataSampleTypingResult = None)
      .deserialize(cr)

    deserialized.key() shouldEqual key
    deserialized.value() shouldEqual record
  }

}
