package pl.touk.nussknacker.engine.standalone.utils

import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import org.everit.json.schema.PrimitiveValidationStrategy
import org.everit.json.schema.loader.SchemaLoader
import org.everit.json.schema.Validator
import org.everit.json.schema.Schema
import org.json.JSONObject
import pl.touk.nussknacker.engine.api.process.SourceTestSupport
import pl.touk.nussknacker.engine.api.test.{NewLineSplittedTestDataParser, TestDataParser}
import pl.touk.nussknacker.engine.api.typed.{TypedMap, _}
import pl.touk.nussknacker.engine.api.{CirceUtil, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.standalone.api.types.GenericResultType
import pl.touk.nussknacker.engine.standalone.api.{ResponseEncoder, StandalonePostSource, StandaloneSourceFactory}
import pl.touk.nussknacker.engine.standalone.utils.typed.SchemaTypingResult
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder
import pl.touk.nussknacker.engine.util.typing.JsonToTypedMapConverter

import java.nio.charset.StandardCharsets

class JsonSchemaStandaloneSourceFactory extends StandaloneSourceFactory[TypedMap] {

  private val jsonEncoder = BestEffortJsonEncoder(failOnUnkown = true, getClass.getClassLoader)

  @MethodToInvoke
  def create(@ParamName("schema") schemaStr: String) : StandalonePostSource[TypedMap] =
    new StandalonePostSource[TypedMap] with LazyLogging with ReturningType with SourceTestSupport[TypedMap] {

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

      override def returnType: typing.TypingResult =
        SchemaTypingResult.jsonSchemaToTypingResult(schemaStr)

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
        val json = CirceUtil.decodeJsonUnsafe[Json](jsonObject.toString, "Provided json is not valid")
        jsonToTypeMap(json)
      }

  }

  override def clazz: Class[_] = classOf[TypedMap]

}

