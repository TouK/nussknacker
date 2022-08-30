package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client

import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.swagger.v3.oas.models.media
import pl.touk.nussknacker.engine.json.swagger.OpenAPISchemaParser

case class OpenAPIJsonSchema(schemaString: String) extends JsonSchema(schemaString) {

  lazy val swaggerJsonSchema: media.Schema[_] = OpenAPISchemaParser.parseSchema(schemaString)
}
