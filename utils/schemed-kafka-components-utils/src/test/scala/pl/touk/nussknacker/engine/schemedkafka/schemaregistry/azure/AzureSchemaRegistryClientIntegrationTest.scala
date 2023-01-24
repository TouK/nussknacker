package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.{Schema, SchemaBuilder}
import org.apache.avro.reflect.ReflectData
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.tags.Network
import pl.touk.nussknacker.engine.kafka.{SchemaRegistryCacheConfig, SchemaRegistryClientKafkaConfig}
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

import scala.beans.BeanProperty

@Network
class AzureSchemaRegistryClientIntegrationTest extends AnyFunSuite with Matchers
  with ValidatedValuesDetailedMessage with OptionValues {

  private val eventHubsNamespace = Option(System.getenv("AZURE_EVENT_HUBS_NAMESPACE")).getOrElse("nu-cloud")

  private val schemaRegistryConfigMap = Map(
    "schema.registry.url" -> s"https://$eventHubsNamespace.servicebus.windows.net",
    "schema.group" -> "test-group")

  private val schemaRegistryConfig = SchemaRegistryClientKafkaConfig(schemaRegistryConfigMap, SchemaRegistryCacheConfig(), None)
  private val schemaRegistryClient = new AzureSchemaRegistryClientFactory().create(schemaRegistryConfig)

  test("getAllTopics should return topic for corresponding schema based on schema name") {
    val givenTopic = "nu-cloud-integration-test"
    registerSchemaMatchingTopicNameConvention(givenTopic)

    val topics = schemaRegistryClient.getAllTopics.validValue

    topics should contain (givenTopic)
  }

  test("getFreshSchema should return version for topic for corresponding schema based on schema name") {
    val givenTopic = "nu-cloud-multiple-versions-test"
    val aFieldOnlySchema = createRecordSchema(givenTopic, _.name("a").`type`(Schema.create(Schema.Type.STRING)).noDefault())
    val abFieldsSchema = createRecordSchema(givenTopic,
      _.name("a").`type`(Schema.create(Schema.Type.STRING)).noDefault()
        .name("b").`type`(Schema.create(Schema.Type.STRING)).withDefault("bDefault"))

    val aFieldOnlySchemaProps = schemaRegistryClient.registerSchemaVersionIfNotExists(aFieldOnlySchema)
    val abFieldsSchemaProps = schemaRegistryClient.registerSchemaVersionIfNotExists(abFieldsSchema)

    val resultForAFieldOnlySchema = schemaRegistryClient.getFreshSchema(givenTopic, Some(aFieldOnlySchemaProps.getVersion), isKey = false).validValue
    val resultForABFieldsSchema = schemaRegistryClient.getFreshSchema(givenTopic, Some(abFieldsSchemaProps.getVersion), isKey = false).validValue

    resultForAFieldOnlySchema.schema shouldEqual aFieldOnlySchema
    resultForAFieldOnlySchema.id.asString shouldEqual aFieldOnlySchemaProps.getId
    resultForABFieldsSchema.schema shouldEqual abFieldsSchema
    resultForABFieldsSchema.id.asString shouldEqual abFieldsSchemaProps.getId
  }

  private def registerSchemaMatchingTopicNameConvention(topicName: String): Unit = {
    val value = new NuCloudIntegrationTestValue
    // Azure Serializer doesn't support reflect schema inferring but generally reflect schema approach shows convention
    // which Avro follow in other places like generated specific record approach)
    val schema = ReflectData.get().getSchema(classOf[NuCloudIntegrationTestValue])
    SchemaNameTopicMatchStrategy.topicNameFromValueSchemaName(schema.getFullName).value shouldEqual topicName

    schemaRegistryClient.registerSchemaVersionIfNotExists(new AvroSchema(schema))
  }
  private def createRecordSchema(topicName: String,
                                 assemblyFields: SchemaBuilder.FieldAssembler[Schema] => SchemaBuilder.FieldAssembler[Schema]) = {
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
