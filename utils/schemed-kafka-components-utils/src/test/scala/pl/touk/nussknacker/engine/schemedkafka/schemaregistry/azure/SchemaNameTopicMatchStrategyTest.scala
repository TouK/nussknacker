package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure

import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName
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
    SchemaNameTopicMatchStrategy.valueSchemaNameFromTopicName(UnspecializedTopicName("foo")) shouldEqual "FooValue"
    SchemaNameTopicMatchStrategy.valueSchemaNameFromTopicName(
      UnspecializedTopicName("foo-bar")
    ) shouldEqual "FooBarValue"
    SchemaNameTopicMatchStrategy.valueSchemaNameFromTopicName(
      UnspecializedTopicName("foo.bar")
    ) shouldEqual "FooBarValue"
  }

  test("should list topics matching schemas") {
    val referenceTopics = List(
      "foo.bar",
      "foo-baz",
      "without.schema"
    ) map (UnspecializedTopicName.apply)
    val schemasToMatch = List(
      "some.namespace.FooBarKey",
      "some.namespace.FooBarValue",
      "some.namespace.FooBazValue",
      "some.namespace.WithoutTopicValue"
    )
    val matchStrategy = SchemaNameTopicMatchStrategy(referenceTopics)
    matchStrategy.getAllMatchingTopics(schemasToMatch, isKey = false) shouldEqual List(
      UnspecializedTopicName("foo.bar"),
      UnspecializedTopicName("foo-baz")
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
      UnspecializedTopicName("foo.bar"),
      schemasToMatch,
      isKey = false
    ) shouldEqual List(
      "some.namespace.FooBarValue"
    )
    SchemaNameTopicMatchStrategy.getMatchingSchemas(
      UnspecializedTopicName("foo-bar"),
      schemasToMatch,
      isKey = false
    ) shouldEqual List(
      "some.namespace.FooBarValue"
    )
    SchemaNameTopicMatchStrategy.getMatchingSchemas(
      UnspecializedTopicName("without.schema"),
      schemasToMatch,
      isKey = false
    ) shouldEqual Nil
  }

}
