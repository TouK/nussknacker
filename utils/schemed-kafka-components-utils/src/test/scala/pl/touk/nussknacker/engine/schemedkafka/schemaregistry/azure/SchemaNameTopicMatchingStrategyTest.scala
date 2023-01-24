package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure

import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.SchemaNameTopicMatchingStrategy.FullSchemaNameDecomposed

class SchemaNameTopicMatchingStrategyTest extends AnyFunSuite with Matchers with OptionValues {

  test("schema name extraction") {
    FullSchemaNameDecomposed.unapply("some.namespace.FooBarKey").value shouldEqual ("foo-bar", Some("some.namespace"), true)
    FullSchemaNameDecomposed.unapply("some.namespace.FooBarValue").value shouldEqual ("foo-bar", Some("some.namespace"), false)
    FullSchemaNameDecomposed.unapply("some.namespace.FooBar") should be (empty)
    FullSchemaNameDecomposed.unapply("FooBarKey").value shouldEqual("foo-bar", None, true)

    SchemaNameTopicMatchingStrategy.topicNameFromKeySchemaName("some.namespace.FooBarKey").value shouldEqual "foo-bar"
    SchemaNameTopicMatchingStrategy.topicNameFromValueSchemaName("some.namespace.FooBarKey") should be(empty)
    SchemaNameTopicMatchingStrategy.topicNameFromKeySchemaName("some.namespace.FooBarValue") should be(empty)
    SchemaNameTopicMatchingStrategy.topicNameFromValueSchemaName("some.namespace.FooBarValue").value shouldEqual "foo-bar"
  }

  test("topic to schema name conversion") {
    SchemaNameTopicMatchingStrategy.keySchemaNameFromTopicName("foo") shouldEqual "FooKey"
    SchemaNameTopicMatchingStrategy.keySchemaNameFromTopicName("foo-bar") shouldEqual "FooBarKey"
    SchemaNameTopicMatchingStrategy.valueSchemaNameFromTopicName("foo") shouldEqual "FooValue"
    SchemaNameTopicMatchingStrategy.valueSchemaNameFromTopicName("foo-bar") shouldEqual "FooBarValue"
  }

}
