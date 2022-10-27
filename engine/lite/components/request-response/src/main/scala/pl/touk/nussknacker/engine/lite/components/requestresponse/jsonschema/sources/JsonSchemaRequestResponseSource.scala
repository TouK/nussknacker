package pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sources

import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import org.everit.json.schema.Schema
import pl.touk.nussknacker.engine.api.process.SourceTestSupport
import pl.touk.nussknacker.engine.api.test.{NewLineSplittedTestDataParser, TestDataParser}
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.api.{CirceUtil, MetaData, NodeId}
import pl.touk.nussknacker.engine.json.SwaggerBasedJsonSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.json.serde.CirceJsonDeserializer
import pl.touk.nussknacker.engine.requestresponse.api.openapi.OpenApiSourceDefinition
import pl.touk.nussknacker.engine.requestresponse.api.{RequestResponsePostSource, ResponseEncoder}
import pl.touk.nussknacker.engine.requestresponse.utils.encode.SchemaResponseEncoder

import java.nio.charset.StandardCharsets

class JsonSchemaRequestResponseSource(val definition: String, metaData: MetaData, inputSchema: Schema, outputSchema: Schema, val nodeId: NodeId)
  extends RequestResponsePostSource[Any] with LazyLogging with ReturningType with SourceTestSupport[Any] {

  protected val openApiDescription: String = s"**scenario name**: ${metaData.id}"

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

  override def testDataParser: TestDataParser[Any] = {
    new NewLineSplittedTestDataParser[Any] {
      override def parseElement(testElement: String): Any = {
        validateAndReturnTypedMap(testElement)
      }
    }
  }

  override def responseEncoder: Option[ResponseEncoder[Any]] = Option(new SchemaResponseEncoder(outputSchema))

  private def decodeJsonWithError(str: String): Json = CirceUtil.decodeJsonUnsafe[Json](str, "Provided json is not valid")

}

