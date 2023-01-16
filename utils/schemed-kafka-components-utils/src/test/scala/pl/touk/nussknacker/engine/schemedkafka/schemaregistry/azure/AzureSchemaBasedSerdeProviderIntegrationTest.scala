package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure

import org.apache.avro.generic.{GenericRecordBuilder, IndexedRecord}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.record.TimestampType
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.tags.Network
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.AvroUtils
import pl.touk.nussknacker.engine.util.KeyedValue

import java.nio.charset.StandardCharsets
import java.util.Optional

/**
  * To run this test you should have configured one of authentication options described here:
  * https://github.com/Azure/azure-sdk-for-java/wiki/Azure-Identity-Examples#authenticating-with-defaultazurecredential
  * e.g. Intellij plugin, Azure CLI or environment variables
  * Test connects to the schema registry registered in your Event Hubs Namespace (it will be taken from AZURE_EVENT_HUBS_NAMESPACE
  * environment variable - by default nu-cloud). Test will auto create pl.touk.nussknacker.test_schema inside test-group there.
  */
@Network
class AzureSchemaBasedSerdeProviderIntegrationTest extends AnyFunSuite with OptionValues with Matchers {

  test("serialization round-trip") {
    val eventHubsNamespace = Option(System.getenv("AZURE_EVENT_HUBS_NAMESPACE")).getOrElse("nu-cloud")
    val config = Map(
      "schema.registry.url" -> s"https://$eventHubsNamespace.servicebus.windows.net",
      "schema.group" -> "test-group",
      "auto.register.schemas" -> "true",
    )
    val schema = AvroUtils.parseSchema(
      """{
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
    val serdeProvider = new AzureSchemaBasedSerdeProvider()
    val kafkaConfig = KafkaConfig(Some(config), None)
    val key = "sample-key"
    val pr = serdeProvider.serializationSchemaFactory
      // TODO: we should check if schema name matches our topic-schema name matching convention in the serializer
      .create("notImportantTopicNme", None,  kafkaConfig)
      .serialize(new KeyedValue[AnyRef, AnyRef](key, record), timestamp = 0L)

    val contentTypeHeader = Option(pr.headers().lastHeader("content-type")).value
    val contentTypeHeaderValue = new String(contentTypeHeader.value(), StandardCharsets.UTF_8)
    contentTypeHeaderValue should startWith ("avro/binary+")

    val cr = new ConsumerRecord[Array[Byte], Array[Byte]](pr.topic(), pr.partition(), 0L, pr.timestamp(), TimestampType.CREATE_TIME,
      pr.key().length, pr.value().length, pr.key(), pr.value(), pr.headers(), Optional.empty[Integer]())
    val deserialized = serdeProvider.deserializationSchemaFactory.create[String, IndexedRecord](kafkaConfig, None, None).deserialize(cr)

    deserialized.key() shouldEqual key
    deserialized.value() shouldEqual record
  }

}
