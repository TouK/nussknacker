package pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sources

import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import org.everit.json.schema.{CombinedSchema, Schema}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{SourceTestSupport, TestWithParametersSupport}
import pl.touk.nussknacker.engine.api.test.{TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.api.{CirceUtil, MetaData, NodeId}
import pl.touk.nussknacker.engine.json.{JsonSchemaBasedParameter, SwaggerBasedJsonSchemaTypeDefinitionExtractor}
import pl.touk.nussknacker.engine.json.serde.CirceJsonDeserializer
import pl.touk.nussknacker.engine.json.swagger.SwaggerTyped
import pl.touk.nussknacker.engine.json.swagger.decode.FromJsonSchemaBasedDecoder
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sinks.JsonRequestResponseSink.SinkRawValueParamName
import pl.touk.nussknacker.engine.requestresponse.api.openapi.OpenApiSourceDefinition
import pl.touk.nussknacker.engine.requestresponse.api.{RequestResponsePostSource, ResponseEncoder}
import pl.touk.nussknacker.engine.requestresponse.utils.encode.SchemaResponseEncoder
import pl.touk.nussknacker.engine.util.json.{JsonSchemaUtils, ToJsonEncoder}
import pl.touk.nussknacker.engine.util.json.JsonSchemaImplicits._
import pl.touk.nussknacker.engine.util.parameters.TestingParametersSupport

import scala.jdk.CollectionConverters._
import java.nio.charset.StandardCharsets

class JsonSchemaRequestResponseSource(
    val definition: String,
    metaData: MetaData,
    inputSchema: Schema,
    outputSchema: Schema,
    val nodeId: NodeId
) extends RequestResponsePostSource[Any]
    with LazyLogging
    with ReturningType
    with SourceTestSupport[Any]
    with TestWithParametersSupport[Any] {

  protected val openApiDescription: String = s"**scenario name**: ${metaData.name}"

  private val deserializer = new CirceJsonDeserializer(inputSchema)

  // So far, we don't expose validation mode as a parameter to not overload the interface. We pick lax mode because
  // we don't want to block user in some edge cases such as output = input but without redundant fields
  private val validationMode = ValidationMode.lax

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

  override def testRecordParser: TestRecordParser[Any] = (testRecords: List[TestRecord]) =>
    testRecords.map { testRecord => validateAndReturnTypedMap(testRecord.json.noSpaces) }

  override def responseEncoder: Option[ResponseEncoder[Any]] = Option(
    new SchemaResponseEncoder(outputSchema, validationMode)
  )

  override def testParametersDefinition: List[Parameter] = {
    JsonSchemaBasedParameter(inputSchema, SinkRawValueParamName, validationMode)(nodeId)
      .map(_.toParameters)
      .valueOr(errors =>
        throw new IllegalArgumentException(s"Cannot provide test parameters definition: ${errors.toList.mkString(" ")}")
      )
  }

  override def parametersToTestData(params: Map[ParameterName, AnyRef]): Any = {
    handleSchemaWithUnionTypes(params)
  }

  // TODO handle anyOf, allOf, oneOf schemas better than 'first matched schema' - now it works kinda' like "anyOf" for all combined schemas
  // 1. Improve editor to handle display of combined schemas e.g. by using raw mode when combined schema occurs
  // 2. In `JsonToNuStruct` handle different types of combined schema, not just first valid occurrence
  private def handleSchemaWithUnionTypes(params: Map[ParameterName, AnyRef]): Any = {
    inputSchema match {
      case cs: CombinedSchema => {
        params
          .get(SinkRawValueParamName)
          .map { paramValue =>
            val json                       = ToJsonEncoder.defaultForTests.encode(paramValue)
            val schema                     = getFirstMatchingSchemaForJson(cs, json)
            val swaggerTyped: SwaggerTyped = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema)
            FromJsonSchemaBasedDecoder.decode(json, swaggerTyped)
          }
          .getOrElse {
            throw new IllegalArgumentException( // Should never happen since CombinedSchema is created using SinkRawValueParamName but still...
              s"Expected combined schema with ${SinkRawValueParamName.value} parameter but $cs was found"
            )
          }
      }
      case _ =>
        val json = ToJsonEncoder.defaultForTests.encode(TestingParametersSupport.unflattenParameters(params))
        val swaggerTyped: SwaggerTyped = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(inputSchema)
        FromJsonSchemaBasedDecoder.decode(json, swaggerTyped)
    }
  }

  private def getFirstMatchingSchemaForJson(combinedSchema: CombinedSchema, json: Json): Schema = {
    combinedSchema.getSubschemas.asScala
      .flatMap { s => s.validateData(JsonSchemaUtils.circeToJson(json)).toOption.map(_ => s) }
      .headOption
      .getOrElse(
        throw new IllegalArgumentException(s"There is no valid input for provided combined schema ${combinedSchema}")
      )
  }

  private def decodeJsonWithError(str: String): Json =
    CirceUtil.decodeJsonUnsafe[Json](str, "Provided json is not valid")
}
