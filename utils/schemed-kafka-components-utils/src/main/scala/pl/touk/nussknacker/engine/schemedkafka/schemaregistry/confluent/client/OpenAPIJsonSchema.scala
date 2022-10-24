package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client

import io.confluent.kafka.schemaregistry.json.JsonSchema
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.json.SwaggerBasedJsonSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.json.serde.CirceJsonDeserializer

case class OpenAPIJsonSchema(schemaString: String) extends JsonSchema(schemaString) {

  val returnType: typing.TypingResult = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(rawSchema()).typingResult

  //we want to create it once, as it can be a bit costly
  val deserializer = new CirceJsonDeserializer(rawSchema())

}
