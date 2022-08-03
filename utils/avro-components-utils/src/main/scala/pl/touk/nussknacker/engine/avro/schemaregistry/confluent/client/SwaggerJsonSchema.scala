package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client

import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.swagger.v3.oas.models.media
import pl.touk.nussknacker.engine.json.SwaggerBasedJsonSchemaTypeDefinitionExtractor

case class SwaggerJsonSchema(schemaString: String) extends JsonSchema(schemaString) {

  lazy val swaggerJsonSchema: media.Schema[_] = SwaggerBasedJsonSchemaTypeDefinitionExtractor.parseSchema(schemaString)
}
