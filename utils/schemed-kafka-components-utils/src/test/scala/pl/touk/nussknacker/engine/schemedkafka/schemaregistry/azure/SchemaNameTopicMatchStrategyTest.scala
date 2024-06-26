package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure

import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.kafka.UncategorizedTopicName
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.SchemaNameTopicMatchStrategy.FullSchemaNameDecomposed

class SchemaNameTopicMatchStrategyTest extends AnyFunSuite with Matchers with OptionValues {

  test("should decompose full schema name") {
    FullSchemaNameDecomposed.unapply("some.namespace.FooBarKey").value shouldEqual ("FooBar", true)
    FullSchemaNameDecomposed.unapply("some.namespace.FooBarValue").value shouldEqual ("FooBar", false)
    FullSchemaNameDecomposed.unapply("some.namespace.FooBar") should be(empty)
    FullSchemaNameDecomposed.unapply("FooBarKey").value shouldEqual ("FooBar", true)
    FullSchemaNameDecomposed.unapply("FooBar") should be(empty)
  }

  test("should convert topic to schema name") {
    SchemaNameTopicMatchStrategy.valueSchemaNameFromTopicName(UncategorizedTopicName("foo")) shouldEqual "FooValue"
    SchemaNameTopicMatchStrategy.valueSchemaNameFromTopicName(
      UncategorizedTopicName("foo-bar")
    ) shouldEqual "FooBarValue"
    SchemaNameTopicMatchStrategy.valueSchemaNameFromTopicName(
      UncategorizedTopicName("foo.bar")
    ) shouldEqual "FooBarValue"
  }

  test("should list topics matching schemas") {
    val referenceTopics = List(
      "foo.bar",
      "foo-baz",
      "without.schema"
    ) map (UncategorizedTopicName.apply)
    val schemasToMatch = List(
      "some.namespace.FooBarKey",
      "some.namespace.FooBarValue",
      "some.namespace.FooBazValue",
      "some.namespace.WithoutTopicValue"
    )
    val matchStrategy = SchemaNameTopicMatchStrategy(referenceTopics)
    matchStrategy.getAllMatchingTopics(schemasToMatch, isKey = false) shouldEqual List(
      UncategorizedTopicName("foo.bar"),
      UncategorizedTopicName("foo-baz")
    )
  }

  test("should list schemas matching reference topics") {
    val schemasToMatch = List(
      "some.namespace.FooBarKey",
      "some.namespace.FooBarValue",
      "some.namespace.FooBazValue",
      "some.namespace.WithoutTopicValue"
    )

    SchemaNameTopicMatchStrategy.getMatchingSchemas(
      UncategorizedTopicName("foo.bar"),
      schemasToMatch,
      isKey = false
    ) shouldEqual List(
      "some.namespace.FooBarValue"
    )
    SchemaNameTopicMatchStrategy.getMatchingSchemas(
      UncategorizedTopicName("foo-bar"),
      schemasToMatch,
      isKey = false
    ) shouldEqual List(
      "some.namespace.FooBarValue"
    )
    SchemaNameTopicMatchStrategy.getMatchingSchemas(
      UncategorizedTopicName("without.schema"),
      schemasToMatch,
      isKey = false
    ) shouldEqual Nil
  }

}
