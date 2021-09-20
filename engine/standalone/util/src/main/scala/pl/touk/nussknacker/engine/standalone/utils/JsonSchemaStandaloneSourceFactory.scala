package pl.touk.nussknacker.engine.standalone.utils

import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import org.everit.json.schema.loader.SchemaLoader
import org.everit.json.schema.Validator
import org.everit.json.schema.Schema
import org.json.JSONObject
import pl.touk.nussknacker.engine.api.process.SourceTestSupport
import pl.touk.nussknacker.engine.api.test.{NewLineSplittedTestDataParser, TestDataParser}
import pl.touk.nussknacker.engine.api.typed.{TypedMap, _}
import pl.touk.nussknacker.engine.api.{CirceUtil, MetaData, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.standalone.api.openapi.OpenApiSourceDefinition
import pl.touk.nussknacker.engine.standalone.api.types.GenericResultType
import pl.touk.nussknacker.engine.standalone.api.{ResponseEncoder, StandalonePostSource, StandaloneSourceFactory}
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder
import pl.touk.nussknacker.engine.util.typing.{JsonToTypedMapConverter, SchemaToTypingResultConverter}

import java.nio.charset.StandardCharsets

class JsonSchemaStandaloneSourceFactory extends StandaloneSourceFactory[TypedMap] {

  private val jsonEncoder = BestEffortJsonEncoder(failOnUnkown = true, getClass.getClassLoader)

  @MethodToInvoke
  def create(@ParamName("schema") schemaStr: String)(implicit metaData: MetaData) : StandalonePostSource[TypedMap] =
    new JsonSchemaStandaloneSource(schemaStr, metaData, jsonEncoder)

  override def clazz: Class[_] = classOf[TypedMap]

}

class JsonSchemaStandaloneSource(schemaStr: String, metaData: MetaData, jsonEncoder: BestEffortJsonEncoder) extends StandalonePostSource[TypedMap] with LazyLogging with ReturningType with SourceTestSupport[TypedMap] {
  protected val validator: Validator = Validator.builder().build()
  protected def prepareSchema(rawSchema: JSONObject) = {
    SchemaLoader.builder()
      .useDefaults(true)
      .schemaJson(rawSchema)
      .draftV7Support()
      .build().load().build()
      .asInstanceOf[Schema]
  }

  private val rawSchema: JSONObject = new JSONObject(schemaStr)
  private val schema: Schema = prepareSchema(rawSchema)

  override def parse(parameters: Array[Byte]): TypedMap = {
    val parametersString = new String(parameters, StandardCharsets.UTF_8)
    validateAndReturnTypedMap(parametersString)
  }

  protected val openApiDescription: String = {
    val properties = metaData.additionalFields.map(_.properties).getOrElse(Map.empty)
    properties.map(v => s"**${v._1}**: ${v._2}").mkString("\\\n")
  }

  override def openApiDefinition: Option[OpenApiSourceDefinition] = {
    val json = decodeJsonWithError(schemaStr)
    Option(OpenApiSourceDefinition(json, openApiDescription, List("Nussknacker")))
  }

  override def returnType: typing.TypingResult = {
    SchemaToTypingResultConverter.jsonSchemaToTypingResult(schema)
  }

  override def testDataParser: TestDataParser[TypedMap] = {
    new NewLineSplittedTestDataParser[TypedMap] {
      override def parseElement(testElement: String): TypedMap = {
        validateAndReturnTypedMap(testElement)
      }
    }
  }

  override def responseEncoder: Option[ResponseEncoder[TypedMap]] = Option(new ResponseEncoder[TypedMap] {
    override def toJsonResponse(input: TypedMap, result: List[Any]): GenericResultType[Json] = {
      val jsonResult = result.map(jsonEncoder.encode)
        .headOption
        .getOrElse(throw new IllegalArgumentException(s"Process did not return any result"))
      Right(jsonResult)
    }
  })

  protected def jsonToTypeMap(json: Json): TypedMap = JsonToTypedMapConverter.jsonToTypedMap(json)

  private def validateAndReturnTypedMap(parameters: String): TypedMap = {
    val jsonObject = new JSONObject(parameters)
    validator.performValidation(schema, jsonObject)
    val json = decodeJsonWithError(jsonObject.toString)
    jsonToTypeMap(json)
  }

  private def decodeJsonWithError(str: String): Json = CirceUtil.decodeJsonUnsafe[Json](str, "Provided json is not valid")

}
