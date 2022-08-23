package pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sources

import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import org.everit.json.schema.{Schema, ValidationException}
import org.json.{JSONException, JSONObject}
import pl.touk.nussknacker.engine.api.process.SourceTestSupport
import pl.touk.nussknacker.engine.api.test.{NewLineSplittedTestDataParser, TestDataParser}
import pl.touk.nussknacker.engine.api.typed.{CustomNodeValidationException, ReturningType, TypedMap, typing}
import pl.touk.nussknacker.engine.api.{CirceUtil, MetaData, NodeId}
import pl.touk.nussknacker.engine.json.SwaggerBasedJsonSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.requestresponse.api.openapi.OpenApiSourceDefinition
import pl.touk.nussknacker.engine.requestresponse.api.{RequestResponsePostSource, ResponseEncoder}
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder
import pl.touk.nussknacker.engine.util.typing.JsonToTypedMapConverter

import java.nio.charset.StandardCharsets
import scala.collection.JavaConverters._

class JsonSchemaRequestResponseSource(val definition: String, metaData: MetaData, schema: Schema, val nodeId: NodeId)
  extends RequestResponsePostSource[TypedMap] with LazyLogging with ReturningType with SourceTestSupport[TypedMap] {
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
    JsonToTypedMapConverter.jsonToTypedMap(json)
  }


  override def openApiDefinition: Option[OpenApiSourceDefinition] = {
    val json = decodeJsonWithError(definition)
    Option(OpenApiSourceDefinition(json, openApiDescription, List("Nussknacker")))
  }

  override def returnType: typing.TypingResult = {
    SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult
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

  private def decodeJsonWithError(str: String): Json = CirceUtil.decodeJsonUnsafe[Json](str, "Provided json is not valid")


  protected def prepareValidationErrorMessage(exception: Throwable): String = {
    exception match {
      case ve: ValidationException => ve.getAllMessages.asScala.mkString("\n\n")
      case je: JSONException => s"Invalid JSON: ${je.getMessage}"
      case _ => "unknown error message"
    }
  }

  protected def catchValidationError[T](action: => T): T = try {
    action
  } catch {
    case ex: Throwable =>
      val errorMsg = prepareValidationErrorMessage(ex)
      throw CustomNodeValidationException(errorMsg, None)
  }

}

