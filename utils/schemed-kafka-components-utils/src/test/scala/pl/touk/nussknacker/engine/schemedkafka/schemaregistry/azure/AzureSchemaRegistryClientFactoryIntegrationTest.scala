package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.reflect.ReflectData
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.tags.Network
import pl.touk.nussknacker.engine.kafka.{SchemaRegistryCacheConfig, SchemaRegistryClientKafkaConfig}
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

import scala.beans.BeanProperty

@Network
class AzureSchemaRegistryClientFactoryIntegrationTest extends AnyFunSuite with Matchers
  with ValidatedValuesDetailedMessage with OptionValues {

  test("getAllTopics should return topic for corresponding reflect schema based on schema name") {
    val eventHubsNamespace = Option(System.getenv("AZURE_EVENT_HUBS_NAMESPACE")).getOrElse("nu-cloud")
    val schemaRegistryConfigMap = Map(
      "schema.registry.url" -> s"https://$eventHubsNamespace.servicebus.windows.net",
      "schema.group" -> "test-group",
    )
    val schemaRegistryConfig = SchemaRegistryClientKafkaConfig(schemaRegistryConfigMap, SchemaRegistryCacheConfig(), None)
    val schemaRegistryClient = new AzureSchemaRegistryClientFactory().create(schemaRegistryConfig)

    val givenTopic = "nu-cloud-integration-test"
    registerSchemaMatchingTopicNameConvention(schemaRegistryClient, givenTopic)

    val topics = schemaRegistryClient.getAllTopics.validValue

    topics should contain (givenTopic)
  }

  private def registerSchemaMatchingTopicNameConvention(client: AzureSchemaRegistryClient, topicName: String): Unit = {
    val value = new NuCloudIntegrationTestValue
    // Azure Serializer doesn't support reflect schema inferring but generally reflect schema approach shows convention
    // which Avro follow in other places like generated specific record approach)
    val schema = ReflectData.get().getSchema(classOf[NuCloudIntegrationTestValue])
    FullSchemaNameDecomposed.topicNameFromValueSchemaName(schema.getFullName).value shouldEqual topicName

    client.registerSchema(new AvroSchema(schema))
  }

}

class NuCloudIntegrationTestValue {

  @BeanProperty
  var aField: String = _

}
