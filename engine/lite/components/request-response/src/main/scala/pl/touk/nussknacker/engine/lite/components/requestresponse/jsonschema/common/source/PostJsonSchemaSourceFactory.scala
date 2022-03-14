package pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.common.source

import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import org.everit.json.schema.{Schema, ValidationException}
import org.json.{JSONException, JSONObject}
import PostJsonSchemaSourceFactory.catchValidationError
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process.SourceTestSupport
import pl.touk.nussknacker.engine.api.test.{NewLineSplittedTestDataParser, TestDataParser}
import pl.touk.nussknacker.engine.api.typed.{CustomNodeValidationException, ReturningType, TypedMap, typing}
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.common.openapi.SwaggerOApiUtils
import pl.touk.nussknacker.engine.requestresponse.api.ResponseEncoder
import pl.touk.nussknacker.engine.requestresponse.api.openapi.OpenApiSourceDefinition
import pl.touk.nussknacker.engine.util.typing.{JsonToTypedMapConverter, SchemaToTypingResultConverter}

import java.nio.charset.StandardCharsets
import scala.collection.JavaConverters._

object PostJsonSchemaSourceFactory {
  final val SchemaParamName = "schema"
  final val PostJsonSchemaSourceId = "post-json-schema-source"

  private[source] def catchValidationError[T](action: => T): T = try {
    action
  } catch {
    case ve: ValidationException => {
      val limitDisplayedErrors = 5
      val allMessages = ve.getAllMessages.asScala
      val allMessagesSize = ve.getAllMessages.size()
      val error = {
        if(allMessagesSize > limitDisplayedErrors) allMessages.take(5)
          .mkString("\n\n")
          .concat(s"...and ${allMessagesSize - limitDisplayedErrors} more.")
        else allMessages.mkString("\n\n")
      }
      throw CustomNodeValidationException(error, Some(SchemaParamName))
    }
    case je: JSONException => {
      throw CustomNodeValidationException(s"Invalid JSON: ${je.getMessage}", Some(SchemaParamName))
    }
  }

}

class PostJsonSchemaSource(val definition: String, metaData: MetaData, schema: Schema, val nodeId: NodeId)
  extends RequestResponsePostSourceWithContextId[TypedMap] with LazyLogging with ReturningType with SourceTestSupport[TypedMap] {

  override def parse(parameters: Array[Byte]): TypedMap = {
    val parametersString = new String(parameters, StandardCharsets.UTF_8)
    validateAndReturnTypedMap(parametersString)
  }

  protected val openApiDescription: String = SwaggerOApiUtils.prepareProcessDescription(metaData.additionalFields)

  override def openApiDefinition: Option[OpenApiSourceDefinition] = {
    val json = decodeJsonWithError(definition)
    Option(OpenApiSourceDefinition(json, openApiDescription, List("Nba")))
  }

  override def returnType: typing.TypingResult = {
    SchemaToTypingResultConverter.jsonSchemaToTypingResult(schema)
  }

  override def responseEncoder: Option[ResponseEncoder[TypedMap]] = PostSourceCommon.responseEncoder(metaData)

  override def testDataParser: TestDataParser[TypedMap] = {
    new NewLineSplittedTestDataParser[TypedMap] {
      override def parseElement(testElement: String): TypedMap = {
        validateAndReturnTypedMap(testElement)
      }
    }
  }

  private def validateAndReturnTypedMap(parameters: String): TypedMap = {
    val jsonObject = new JSONObject(parameters)
    catchValidationError(schema.validate(jsonObject))
    val json = decodeJsonWithError(jsonObject.toString)
    JsonToTypedMapConverter.jsonToTypedMap(json)
  }

  private def decodeJsonWithError(str: String): Json = CirceUtil.decodeJsonUnsafe[Json](str, "Provided json is not valid")

}