package pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.common.sources

import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.common.sources.JsonSchemaRequestResponseSource.SchemaParamName
import org.everit.json.schema.{Schema, ValidationException, Validator}
import org.json.{JSONException, JSONObject}
import pl.touk.nussknacker.engine.api.process.SourceTestSupport
import pl.touk.nussknacker.engine.api.test.{NewLineSplittedTestDataParser, TestDataParser}
import pl.touk.nussknacker.engine.api.typed.{CustomNodeValidationException, ReturningType, TypedMap, typing}
import pl.touk.nussknacker.engine.api.{CirceUtil, MetaData, NodeId}
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.jsonschemautils.JsonSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.requestresponse.api.openapi.OpenApiSourceDefinition
import pl.touk.nussknacker.engine.requestresponse.api.{RequestResponsePostSource, ResponseEncoder}
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder
import pl.touk.nussknacker.engine.util.typing.JsonToTypedMapConverter

import scala.collection.JavaConverters._
import java.nio.charset.StandardCharsets

object JsonSchemaRequestResponseSource {
  final val SchemaParamName = "schema"
  final val PostJsonSchemaSourceId = "post-json-schema-source"
}


class JsonSchemaRequestResponseSource(val definition: String, metaData: MetaData, schema: Schema, val nodeId: NodeId) extends RequestResponsePostSource[TypedMap] with LazyLogging with ReturningType with SourceTestSupport[TypedMap] {
  protected val validator: Validator = Validator.builder().build()
  protected val openApiDescription: String = s"**scenario name**: ${metaData.id}"
  private val jsonEncoder = BestEffortJsonEncoder(failOnUnkown = true, getClass.getClassLoader)

  override def parse(parameters: Array[Byte]): TypedMap = {
    val parametersString = new String(parameters, StandardCharsets.UTF_8)
    validateAndReturnTypedMap(parametersString)
  }

  private def validateAndReturnTypedMap(parameters: String): TypedMap = {
    val jsonObject = new JSONObject(parameters)
    catchValidationError(schema.validate(jsonObject))
    val json = decodeJsonWithError(jsonObject.toString)
    jsonToTypeMap(json)
  }

  protected def jsonToTypeMap(json: Json): TypedMap = JsonToTypedMapConverter.jsonToTypedMap(json)

  private def decodeJsonWithError(str: String): Json = CirceUtil.decodeJsonUnsafe[Json](str, "Provided json is not valid")

  override def openApiDefinition: Option[OpenApiSourceDefinition] = {
    val json = decodeJsonWithError(definition)
    Option(OpenApiSourceDefinition(json, openApiDescription, List("Nussknacker")))
  }

  override def returnType: typing.TypingResult = {
    JsonSchemaTypeDefinitionExtractor.typeDefinition(schema)
  }

  override def testDataParser: TestDataParser[TypedMap] = {
    new NewLineSplittedTestDataParser[TypedMap] {
      override def parseElement(testElement: String): TypedMap = {
        validateAndReturnTypedMap(testElement)
      }
    }
  }

  override def responseEncoder: Option[ResponseEncoder[TypedMap]] = Option(new ResponseEncoder[TypedMap] {
    override def toJsonResponse(input: TypedMap, result: List[Any]): Json = {
      result.map(jsonEncoder.encode)
        .headOption
        .getOrElse(throw new IllegalArgumentException(s"Process did not return any result"))
    }
  })

  protected val limitDisplayedErrors: Int = 5

  protected def catchValidationError[T](action: => T): T = try {
    action
  } catch {
    case ve: ValidationException =>
      val allMessages = ve.getAllMessages.asScala
      val allMessagesSize = ve.getAllMessages.size()
      val error = {
        if(allMessagesSize > limitDisplayedErrors) allMessages.take(limitDisplayedErrors)
          .mkString("\n\n")
          .concat(s"...and ${allMessagesSize - limitDisplayedErrors} more.")
        else allMessages.mkString("\n\n")
      }
      throw CustomNodeValidationException(error, Option(SchemaParamName))

    case je: JSONException =>
      throw CustomNodeValidationException(s"Invalid JSON: ${je.getMessage}", Option(SchemaParamName))
  }

}

