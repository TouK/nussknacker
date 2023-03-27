package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure

import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.SchemaNameTopicMatchStrategy.FullSchemaNameDecomposed

class SchemaNameTopicMatchStrategyTest extends AnyFunSuite with Matchers with OptionValues {

  test("schema name extraction") {
    FullSchemaNameDecomposed.unapply("some.namespace.FooBarKey").value shouldEqual ("foo-bar", Some("some.namespace"), true)
    FullSchemaNameDecomposed.unapply("some.namespace.FooBarValue").value shouldEqual ("foo-bar", Some("some.namespace"), false)
    FullSchemaNameDecomposed.unapply("some.namespace.FooBar") should be (empty)
    FullSchemaNameDecomposed.unapply("FooBarKey").value shouldEqual("foo-bar", None, true)

    SchemaNameTopicMatchStrategy.topicNameFromKeySchemaName("some.namespace.FooBarKey").value shouldEqual "foo-bar"
    SchemaNameTopicMatchStrategy.topicNameFromValueSchemaName("some.namespace.FooBarKey") should be(empty)
    SchemaNameTopicMatchStrategy.topicNameFromKeySchemaName("some.namespace.FooBarValue") should be(empty)
    SchemaNameTopicMatchStrategy.topicNameFromValueSchemaName("some.namespace.FooBarValue").value shouldEqual "foo-bar"
  }

  test("topic to schema name conversion") {
    SchemaNameTopicMatchStrategy.keySchemaNameFromTopicName("foo") shouldEqual "FooKey"
    SchemaNameTopicMatchStrategy.keySchemaNameFromTopicName("foo-bar") shouldEqual "FooBarKey"
    SchemaNameTopicMatchStrategy.valueSchemaNameFromTopicName("foo") shouldEqual "FooValue"
    SchemaNameTopicMatchStrategy.valueSchemaNameFromTopicName("foo-bar") shouldEqual "FooBarValue"
  }

}
