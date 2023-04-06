package pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sources

import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import org.everit.json.schema.Schema
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.{SourceTestSupport, TestViewGenerator}
import pl.touk.nussknacker.engine.api.test.{TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.api.{CirceUtil, MetaData, NodeId}
import pl.touk.nussknacker.engine.json.{JsonSchemaExtractor, JsonSinkValueParameter, SwaggerBasedJsonSchemaTypeDefinitionExtractor}
import pl.touk.nussknacker.engine.json.serde.CirceJsonDeserializer
import pl.touk.nussknacker.engine.requestresponse.api.openapi.OpenApiSourceDefinition
import pl.touk.nussknacker.engine.requestresponse.api.{RequestResponsePostSource, ResponseEncoder}
import pl.touk.nussknacker.engine.requestresponse.api.openapi.RequestResponseOpenApiSettings.OutputSchemaProperty
import pl.touk.nussknacker.engine.requestresponse.utils.encode.SchemaResponseEncoder

import java.nio.charset.StandardCharsets

class JsonSchemaRequestResponseSource(val definition: String, metaData: MetaData, inputSchema: Schema, outputSchema: Schema, val nodeId: NodeId)
  extends RequestResponsePostSource[Any] with LazyLogging with ReturningType with SourceTestSupport[Any] with TestViewGenerator {

  protected val openApiDescription: String = s"**scenario name**: ${metaData.id}"

  private val jsonSchemaExtractor = new JsonSchemaExtractor()
  private val deserializer = new CirceJsonDeserializer(inputSchema)

  override def parse(parameters: Array[Byte]): Any = {
    val parametersString = new String(parameters, StandardCharsets.UTF_8)
    validateAndReturnTypedMap(parametersString)
  }

  private def validateAndReturnTypedMap(parameters: String): Any = {
    deserializer.deserialize(parameters)
  }


  override def openApiDefinition: Option[OpenApiSourceDefinition] = {
    val json = decodeJsonWithError(definition)
    Option(OpenApiSourceDefinition(json, openApiDescription, List("Nussknacker")))
  }

  override def returnType: typing.TypingResult = {
    SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(inputSchema).typingResult
  }

  override def testRecordParser: TestRecordParser[Any] = (testRecord: TestRecord) => {
    validateAndReturnTypedMap(testRecord.json.noSpaces)
  }

  override def responseEncoder: Option[ResponseEncoder[Any]] = Option(new SchemaResponseEncoder(outputSchema))

  private def decodeJsonWithError(str: String): Json = CirceUtil.decodeJsonUnsafe[Json](str, "Provided json is not valid")

  override def createTestView: List[Parameter] = jsonSchemaExtractor.getSchemaFromProperty(OutputSchemaProperty, metaData, nodeId)
    .andThen { schema =>
      JsonSinkValueParameter(schema, "not-sure-yet", ValidationMode.lax)(nodeId).map(_.toParameters)
    }.valueOr(e => Nil) //TODO error handling ???
}

