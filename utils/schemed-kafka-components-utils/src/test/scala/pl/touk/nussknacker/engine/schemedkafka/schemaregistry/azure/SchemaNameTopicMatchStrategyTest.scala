package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure

import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.SchemaNameTopicMatchStrategy.FullSchemaNameDecomposed

class SchemaNameTopicMatchStrategyTest extends AnyFunSuite with Matchers with OptionValues {

  test("should decompose full schema name") {
    FullSchemaNameDecomposed.unapply("some.namespace.FooBarKey").value shouldEqual ("FooBar", Some(
      "some.namespace"
    ), true)
    FullSchemaNameDecomposed.unapply("some.namespace.FooBarValue").value shouldEqual ("FooBar", Some(
      "some.namespace"
    ), false)
    FullSchemaNameDecomposed.unapply("some.namespace.FooBar") should be(empty)
    FullSchemaNameDecomposed.unapply("FooBarKey").value shouldEqual ("FooBar", None, true)
    FullSchemaNameDecomposed.unapply("FooBar") should be(empty)
  }

  test("should convert topic to schema name") {
    SchemaNameTopicMatchStrategy.keySchemaNameFromTopicName("foo") shouldEqual "FooKey"
    SchemaNameTopicMatchStrategy.keySchemaNameFromTopicName("foo-bar") shouldEqual "FooBarKey"
    SchemaNameTopicMatchStrategy.keySchemaNameFromTopicName("foo.bar") shouldEqual "FooBarKey"
    SchemaNameTopicMatchStrategy.valueSchemaNameFromTopicName("foo") shouldEqual "FooValue"
    SchemaNameTopicMatchStrategy.valueSchemaNameFromTopicName("foo-bar") shouldEqual "FooBarValue"
    SchemaNameTopicMatchStrategy.valueSchemaNameFromTopicName("foo.bar") shouldEqual "FooBarValue"
  }

  test("should list topics matching schemas") {
    val referenceTopics = List(
      "foo.bar",
      "foo-baz",
      "without.schema"
    )
    val schemasToMatch = List(
      "some.namespace.FooBarKey",
      "some.namespace.FooBarValue",
      "some.namespace.FooBazValue",
      "some.namespace.WithoutTopicValue"
    )
    val matchStrategy = SchemaNameTopicMatchStrategy(referenceTopics)
    matchStrategy.matchAllTopics(schemasToMatch, isKey = false) shouldEqual List(
      "foo.bar",
      "foo-baz"
    )
  }

  test("should list schemas matching reference topics") {
    val referenceTopics = List(
      "foo.bar",
      "foo-baz",
      "without.schema"
    )
    val schemasToMatch = List(
      "some.namespace.FooBarKey",
      "some.namespace.FooBarValue",
      "some.namespace.FooBazValue",
      "some.namespace.WithoutTopicValue"
    )
    val matchStrategy = SchemaNameTopicMatchStrategy(referenceTopics)
    matchStrategy.matchAllSchemas(schemasToMatch, isKey = false) shouldEqual List(
      "some.namespace.FooBarValue",
      "some.namespace.FooBazValue"
    )
  }

}
