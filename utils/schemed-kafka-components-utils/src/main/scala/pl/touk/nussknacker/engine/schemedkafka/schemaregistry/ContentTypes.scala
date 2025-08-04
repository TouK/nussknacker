package pl.touk.nussknacker.engine.schemedkafka.schemaregistry

import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.OpenAPIJsonSchema

object ContentTypes extends Enumeration {
  type ContentType = Value

  val JSON, PLAIN = Value
}

object ContentTypesSchemas {
  val schemaForJson: OpenAPIJsonSchema = OpenAPIJsonSchema("{}")
  // note: "" deserializes to com.fasterxml.jackson.databind.node.MissingNode,
  // which causes NPE when using Jackson 2.18.3+, because from this version on it decodes to null
  val schemaForPlain: OpenAPIJsonSchema = OpenAPIJsonSchema("")
}
