package pl.touk.nussknacker.engine.requestresponse.utils

import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import org.everit.json.schema.{Schema, Validator}
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject
import pl.touk.nussknacker.engine.api.process.SourceTestSupport
import pl.touk.nussknacker.engine.api.test.{NewLineSplittedTestDataParser, TestDataParser}
import pl.touk.nussknacker.engine.api.typed._
import pl.touk.nussknacker.engine.api.{CirceUtil, MetaData, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.requestresponse.api.openapi.OpenApiSourceDefinition
import pl.touk.nussknacker.engine.requestresponse.api.{RequestResponsePostSource, RequestResponseSourceFactory, ResponseEncoder}
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder
import pl.touk.nussknacker.engine.util.typing.{JsonToTypedMapConverter, SchemaToTypingResultConverter}

import java.nio.charset.StandardCharsets

class JsonSchemaRequestResponseSourceFactory extends RequestResponseSourceFactory {

  private val jsonEncoder = BestEffortJsonEncoder(failOnUnkown = true, getClass.getClassLoader)

  @MethodToInvoke(returnType = classOf[TypedMap])
  def create(@ParamName("schema") schemaStr: String)(implicit metaData: MetaData, nodeId: NodeId): RequestResponsePostSource[TypedMap] =
    new JsonSchemaRequestResponseSource(nodeId, schemaStr, metaData, jsonEncoder)

}

class JsonSchemaRequestResponseSource(val nodeId: NodeId, schemaStr: String, metaData: MetaData, jsonEncoder: BestEffortJsonEncoder) extends RequestResponsePostSource[TypedMap] with LazyLogging with ReturningType with SourceTestSupport[TypedMap] {
  protected val validator: Validator = Validator.builder().build()
  protected val openApiDescription: String = s"**scenario name**: ${metaData.id}"
  private val rawSchema: JSONObject = new JSONObject(schemaStr)
  private val schema: Schema = prepareSchema(rawSchema)

  override def parse(parameters: Array[Byte]): TypedMap = {
    val parametersString = new String(parameters, StandardCharsets.UTF_8)
    validateAndReturnTypedMap(parametersString)
  }

  private def validateAndReturnTypedMap(parameters: String): TypedMap = {
    val jsonObject = new JSONObject(parameters)
    validator.performValidation(schema, jsonObject)
    val json = decodeJsonWithError(jsonObject.toString)
    jsonToTypeMap(json)
  }

  protected def jsonToTypeMap(json: Json): TypedMap = JsonToTypedMapConverter.jsonToTypedMap(json)

  private def decodeJsonWithError(str: String): Json = CirceUtil.decodeJsonUnsafe[Json](str, "Provided json is not valid")

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
    override def toJsonResponse(input: TypedMap, result: List[Any]): Json = {
      result.map(jsonEncoder.encode)
        .headOption
        .getOrElse(throw new IllegalArgumentException(s"Process did not return any result"))
    }
  })

  protected def prepareSchema(rawSchema: JSONObject): Schema = {
    SchemaLoader.builder()
      .useDefaults(true)
      .schemaJson(rawSchema)
      .draftV7Support()
      .build().load().build()
      .asInstanceOf[Schema]
  }

}
