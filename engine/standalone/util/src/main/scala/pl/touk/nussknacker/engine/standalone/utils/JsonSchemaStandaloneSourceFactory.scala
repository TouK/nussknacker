package pl.touk.nussknacker.engine.standalone.utils

import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import org.everit.json.schema.Schema
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject
import pl.touk.nussknacker.engine.api.process.SourceTestSupport
import pl.touk.nussknacker.engine.api.test.{NewLineSplittedTestDataParser, TestDataParser}
import pl.touk.nussknacker.engine.api.typed.{TypedMap, _}
import pl.touk.nussknacker.engine.api.{CirceUtil, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.standalone.api.types.GenericResultType
import pl.touk.nussknacker.engine.standalone.api.{ResponseEncoder, StandalonePostSource, StandaloneSourceFactory}
import pl.touk.nussknacker.engine.standalone.utils.typed.{SchemaTypingResult, TypedMapUtils}
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

import java.nio.charset.StandardCharsets

class JsonSchemaStandaloneSourceFactory extends StandaloneSourceFactory[TypedMap] {

  private val jsonEncoder = BestEffortJsonEncoder(failOnUnkown = true, getClass.getClassLoader)

  @MethodToInvoke
  def create(@ParamName("schema") schemaStr: String) : StandalonePostSource[TypedMap] =
    new StandalonePostSource[TypedMap] with LazyLogging with ReturningType with SourceTestSupport[TypedMap] {

      val rawSchema: JSONObject = new JSONObject(schemaStr)
      val schema: Schema = SchemaLoader.builder()
        .useDefaults(true)
        .schemaJson(rawSchema)
        .draftV7Support()
        .build().load().build()
        .asInstanceOf[Schema]

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

      private def validateAndReturnTypedMap(parameters: String): TypedMap = {
        val jsonObject = new JSONObject(parameters)
        schema.validate(jsonObject)
        val json = CirceUtil.decodeJsonUnsafe[Json](jsonObject.toString, "Provided json is not valid")
        TypedMapUtils.jsonToTypedMap(json)
      }

  }

  override def clazz: Class[_] = classOf[TypedMap]

}

