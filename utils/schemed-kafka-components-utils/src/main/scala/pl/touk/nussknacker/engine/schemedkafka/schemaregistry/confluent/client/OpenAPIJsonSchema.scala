package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client

import io.confluent.kafka.schemaregistry.json.JsonSchema
import org.everit.json.schema.Schema
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.json.{JsonSchemaParser, SwaggerBasedJsonSchemaTypeDefinitionExtractor}
import pl.touk.nussknacker.engine.json.serde.CirceJsonDeserializer

import scala.collection.JavaConverters._

case class OpenAPIJsonSchema(schemaString: String) extends JsonSchema(schemaString) {

  private val schema = {
    val jsonSchemaParser = new JsonSchemaParser(resolvedReferences.asScala.toMap)
    jsonSchemaParser.parseSchema(schemaString)
  }

  val returnType: typing.TypingResult = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult

  val deserializer = new CirceJsonDeserializer(schema)

  override def rawSchema(): Schema = schema

}
