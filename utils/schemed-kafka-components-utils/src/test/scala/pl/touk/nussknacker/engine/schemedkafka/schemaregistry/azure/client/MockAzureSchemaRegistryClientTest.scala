package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.client

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.SchemaBuilder
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{
  ExistingSchemaVersion,
  LatestSchemaVersion,
  SchemaId,
  SchemaTopicError,
  SchemaVersionError
}
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

class MockAzureSchemaRegistryClientTest
    extends AnyFunSuite
    with Matchers
    with ValidatedValuesDetailedMessage
    with OptionValues {

  private val topic = UnspecializedTopicName("foo-bar")

  test("registerSchema + getSchemaById round trip") {
    val client = new MockAzureSchemaRegistryClient
    val schema = createRecordSchema("a")

    val id = client.registerSchema(topic, isKey = false, schema)

    val stored = client.getSchemaById(id)
    stored.schema shouldEqual schema
    stored.id shouldEqual id

    client.getAllTopics.validValue shouldEqual List(topic)
  }

  test("registering the same schema content twice returns the same id and keeps one version") {
    val client = new MockAzureSchemaRegistryClient
    val schema = createRecordSchema("a")

    val id1 = client.registerSchema(topic, isKey = false, schema)
    val id2 = client.registerSchema(topic, isKey = false, schema)

    id1 shouldEqual id2
    client.getAllVersions(topic, isKey = false).validValue shouldEqual List(Integer.valueOf(1))
    client.getAllTopics.validValue shouldEqual List(topic)
  }

  test("registering different schemas creates subsequent versions") {
    val client = new MockAzureSchemaRegistryClient
    val v1     = createRecordSchema("a")
    val v2     = createRecordSchema("b")

    client.registerSchema(topic, isKey = false, v1)
    client.registerSchema(topic, isKey = false, v2)

    client.getAllVersions(topic, isKey = false).validValue shouldEqual List(
      Integer.valueOf(1),
      Integer.valueOf(2)
    )
  }

  test("getLatestFreshSchema returns the highest-version registration") {
    val client = new MockAzureSchemaRegistryClient
    val v1     = createRecordSchema("a")
    val v2     = createRecordSchema("b")

    client.registerSchema(topic, isKey = false, v1)
    val v2Id = client.registerSchema(topic, isKey = false, v2)

    val result = client.getFreshSchema(topic, LatestSchemaVersion, isKey = false).validValue
    result.schema shouldEqual v2
    result.id shouldEqual v2Id
  }

  test("getByTopicAndVersion returns schema for the given version") {
    val client = new MockAzureSchemaRegistryClient
    val v1     = createRecordSchema("a")
    val v2     = createRecordSchema("b")

    val v1Id = client.registerSchema(topic, isKey = false, v1)
    client.registerSchema(topic, isKey = false, v2)

    val result = client.getFreshSchema(topic, ExistingSchemaVersion(1), isKey = false).validValue
    result.schema shouldEqual v1
    result.id shouldEqual v1Id
  }

  test("getAllTopics lists topics whose schemas have been registered") {
    val client = new MockAzureSchemaRegistryClient
    client.registerSchema(topic, isKey = false, createRecordSchema("a"))

    client.getAllTopics.validValue should contain(topic)
  }

  test("record schema name must match topic-derived naming convention") {
    val client = new MockAzureSchemaRegistryClient
    val wrongName = new AvroSchema(
      SchemaBuilder.record("UnrelatedName").fields().requiredString("a").endRecord()
    )

    assertThrows[IllegalArgumentException] {
      client.registerSchema(topic, isKey = false, wrongName)
    }
  }

  test("getFreshSchema returns SchemaTopicError when no schema is registered for the topic") {
    val client       = new MockAzureSchemaRegistryClient
    val unknownTopic = UnspecializedTopicName("missing")

    client
      .getFreshSchema(unknownTopic, LatestSchemaVersion, isKey = false)
      .invalidValue shouldBe a[SchemaTopicError]
  }

  test("getFreshSchema returns SchemaVersionError when the version does not exist") {
    val client = new MockAzureSchemaRegistryClient
    client.registerSchema(topic, isKey = false, createRecordSchema("a"))

    client
      .getFreshSchema(topic, ExistingSchemaVersion(42), isKey = false)
      .invalidValue shouldBe a[SchemaVersionError]
  }

  test("getSchemaById throws when id is unknown") {
    val client = new MockAzureSchemaRegistryClient

    assertThrows[IllegalArgumentException] {
      client.getSchemaById(SchemaId.fromString("does-not-exist"))
    }
  }

  test("register allows providing an explicit schema id and version") {
    val client = new MockAzureSchemaRegistryClient
    val schema = createRecordSchema("a")

    val id = client.register(topic, isKey = false, schema, version = 7, id = "explicit-id")

    id shouldEqual SchemaId.fromString("explicit-id")
    client.getAllVersions(topic, isKey = false).validValue shouldEqual List(Integer.valueOf(7))
    client.getSchemaById(SchemaId.fromString("explicit-id")).schema shouldEqual schema
  }

  test("builder registers schemas and build returns a populated client") {
    val schema = SchemaBuilder.record("FooBarValue").fields().requiredString("a").endRecord()

    val client = new MockAzureSchemaRegistryClientBuilder()
      .register(topic.name, schema, version = 1, isKey = false, schemaId = "schema-1")
      .build

    client.getSchemaById(SchemaId.fromString("schema-1")).schema shouldEqual new AvroSchema(schema)
    client.getAllVersions(topic, isKey = false).validValue shouldEqual List(Integer.valueOf(1))
    client.getAllTopics.validValue shouldEqual List(topic)
  }

  private def createRecordSchema(fieldName: String): AvroSchema =
    new AvroSchema(SchemaBuilder.record("FooBarValue").fields().requiredString(fieldName).endRecord())

}
