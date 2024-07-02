package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.{Schema, SchemaBuilder}
import org.apache.avro.reflect.ReflectData
import org.apache.kafka.clients.admin.NewTopic
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.tags.Network
import pl.touk.nussknacker.engine.kafka.{
  KafkaConfig,
  KafkaUtils,
  SchemaRegistryCacheConfig,
  SchemaRegistryClientKafkaConfig,
  UnspecializedTopicName
}
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

import java.util.Collections
import scala.beans.BeanProperty

@Network
class AzureSchemaRegistryClientIntegrationTest
    extends AnyFunSuite
    with Matchers
    with ValidatedValuesDetailedMessage
    with OptionValues {

  private val eventHubsNamespace = Option(System.getenv("AZURE_EVENT_HUBS_NAMESPACE")).getOrElse("nu-cloud")
  private val eventHubsSharedAccessKeyName =
    Option(System.getenv("AZURE_EVENT_HUBS_SHARED_ACCESS_KEY_NAME")).getOrElse("unknown")
  private val eventHubsSharedAccessKey =
    Option(System.getenv("AZURE_EVENT_HUBS_SHARED_ACCESS_KEY")).getOrElse("unknown")

  // See https://nussknacker.io/documentation/cloud/azure/#setting-up-nussknacker-cloud
  private val kafkaPropertiesConfigMap =
    Map(
      "bootstrap.servers" -> s"$eventHubsNamespace.servicebus.windows.net:9093",
      "security.protocol" -> "SASL_SSL",
      "sasl.mechanism"    -> "PLAIN",
      "sasl.jaas.config" -> s"""org.apache.kafka.common.security.plain.PlainLoginModule required username="$$ConnectionString" password="Endpoint=sb://$eventHubsNamespace.servicebus.windows.net/;SharedAccessKeyName=$eventHubsSharedAccessKeyName;SharedAccessKey=$eventHubsSharedAccessKey";""",
      "schema.registry.url" -> s"https://$eventHubsNamespace.servicebus.windows.net",
      "schema.group"        -> "test-group",
    )

  private val schemaRegistryConfig =
    SchemaRegistryClientKafkaConfig(kafkaPropertiesConfigMap, SchemaRegistryCacheConfig(), None)
  private val schemaRegistryClient = AzureSchemaRegistryClientFactory.create(schemaRegistryConfig)

  test("getAllTopics should return topic for corresponding schema based on schema name") {
    val givenTopic = UnspecializedTopicName("nu-cloud-integration-test")
    registerTopic(givenTopic)

    val value  = new NuCloudIntegrationTestValue
    val schema = createReflectSchema(value)
    SchemaNameTopicMatchStrategy(List(givenTopic))
      .getAllMatchingTopics(List(schema.name()), isKey = false) shouldEqual List(
      givenTopic
    )
    schemaRegistryClient.registerSchemaVersionIfNotExists(schema)

    val topics = schemaRegistryClient.getAllTopics.validValue
    topics should contain(givenTopic)
  }

  test("getFreshSchema should return version for topic for corresponding schema based on schema name") {
    val givenTopic = UnspecializedTopicName("nu-cloud-multiple-versions-test")
    registerTopic(givenTopic)

    val aFieldOnlySchema =
      createRecordSchema(givenTopic, _.name("a").`type`(Schema.create(Schema.Type.STRING)).noDefault())
    val abFieldsSchema = createRecordSchema(
      topicName = givenTopic,
      assemblyFields = _.name("a")
        .`type`(Schema.create(Schema.Type.STRING))
        .noDefault()
        .name("b")
        .`type`(Schema.create(Schema.Type.STRING))
        .withDefault("bDefault")
    )

    val aFieldOnlySchemaProps = schemaRegistryClient.registerSchemaVersionIfNotExists(aFieldOnlySchema)
    val abFieldsSchemaProps   = schemaRegistryClient.registerSchemaVersionIfNotExists(abFieldsSchema)

    val resultForAFieldOnlySchema =
      schemaRegistryClient.getFreshSchema(givenTopic, Some(aFieldOnlySchemaProps.getVersion), isKey = false).validValue
    val resultForABFieldsSchema =
      schemaRegistryClient.getFreshSchema(givenTopic, Some(abFieldsSchemaProps.getVersion), isKey = false).validValue

    resultForAFieldOnlySchema.schema shouldEqual aFieldOnlySchema
    resultForAFieldOnlySchema.id.asString shouldEqual aFieldOnlySchemaProps.getId
    resultForABFieldsSchema.schema shouldEqual abFieldsSchema
    resultForABFieldsSchema.id.asString shouldEqual abFieldsSchemaProps.getId
  }

  private def createReflectSchema[T](value: T): AvroSchema = {
    // Azure Serializer doesn't support reflect schema inferring but generally reflect schema approach shows convention
    // which Avro follow in other places like generated specific record approach)
    val schema = ReflectData.get().getSchema(classOf[NuCloudIntegrationTestValue])
    new AvroSchema(schema)
  }

  private def registerTopic(topicName: UnspecializedTopicName): Unit = {
    val kafkaConfig = KafkaConfig(Some(schemaRegistryConfig.kafkaProperties), None)
    KafkaUtils.usingAdminClient(kafkaConfig) {
      _.createTopics(Collections.singletonList[NewTopic](new NewTopic(topicName.name, Collections.emptyMap())))
    }
  }

  private def createRecordSchema(
      topicName: UnspecializedTopicName,
      assemblyFields: SchemaBuilder.FieldAssembler[Schema] => SchemaBuilder.FieldAssembler[Schema]
  ): AvroSchema = {
    val fields = SchemaBuilder
      .record(SchemaNameTopicMatchStrategy.valueSchemaNameFromTopicName(topicName))
      .namespace("not.important.namespace")
      .fields()
    new AvroSchema(assemblyFields(fields).endRecord())
  }

}

class NuCloudIntegrationTestValue {

  @BeanProperty
  var aField: String = _

}
