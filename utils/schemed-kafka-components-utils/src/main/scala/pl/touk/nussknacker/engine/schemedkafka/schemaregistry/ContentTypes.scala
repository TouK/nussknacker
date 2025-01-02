package pl.touk.nussknacker.engine.schemedkafka.schemaregistry

import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.OpenAPIJsonSchema

object ContentTypes extends Enumeration {
  type ContentType = Value

  val JSON, PLAIN = Value
}

object ContentTypesSchemas {
  val schemaForJson: OpenAPIJsonSchema  = OpenAPIJsonSchema("{}")
  val schemaForPlain: OpenAPIJsonSchema = OpenAPIJsonSchema("")
}
