package pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.common.source

import io.circe.Json
import pl.touk.nussknacker.engine.api.process.SourceTestSupport
import pl.touk.nussknacker.engine.api.test.{NewLineSplittedTestDataParser, TestDataParser}
import pl.touk.nussknacker.engine.api.typed.{ReturningType, TypedMap, typing}
import pl.touk.nussknacker.engine.api.{CirceUtil, MetaData, MethodToInvoke, NodeId, ParamName}
import pl.touk.nussknacker.engine.requestresponse.api.openapi.OpenApiSourceDefinition
import pl.touk.nussknacker.engine.requestresponse.api.{RequestResponsePostSource, RequestResponseSourceFactory, ResponseEncoder}
import pl.touk.nussknacker.engine.util.typing.{JsonToTypedMapConverter, TypingUtils}
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.common.openapi.{SwaggerOApiUtils, TypingResultToJsonSchemaConverter}
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.common.util.JsonEncodings

class PostGenericSourceFactory extends RequestResponseSourceFactory {

  @MethodToInvoke
  def create(@ParamName("type") definition: java.util.Map[String, Any])
            (implicit metaData: MetaData, nodeId: NodeId): RequestResponsePostSource[TypedMap] = new PostGenericSource(definition, metaData, nodeId)

}

class PostGenericSource(val definition: java.util.Map[String, Any], metaData: MetaData, val nodeId: NodeId) extends RequestResponsePostSourceWithContextId[TypedMap] with ReturningType with SourceTestSupport[TypedMap] {

  case class PostGenericAdditionalFields(responseAggregation: String, withResponseId: Boolean, rootName: String)

  override def parse(parameters: Array[Byte]): TypedMap = {
    val json = CirceUtil.decodeJsonUnsafe[Json](parameters)
    JsonToTypedMapConverter.jsonToTypedMap(json)
  }


  override def openApiDefinition: Option[OpenApiSourceDefinition] = {

    val jsonSchemaConverter = new TypingResultToJsonSchemaConverter(Map.empty)

    Option(
      OpenApiSourceDefinition(
        JsonEncodings.encodeJson(Map("type" -> "object", "properties" -> jsonSchemaConverter.typingResultToJsonSchema(returnType))),
        SwaggerOApiUtils.prepareProcessDescription(metaData.additionalFields),
        List("Nba")
      )
    )
  }

  override def returnType: typing.TypingResult = {
    TypingUtils.typeMapDefinition(definition)
  }

  override def responseEncoder: Option[ResponseEncoder[TypedMap]] = PostSourceCommon.responseEncoder(metaData)

  override def testDataParser: TestDataParser[TypedMap] = {
    new NewLineSplittedTestDataParser[TypedMap] {
      override def parseElement(testElement: String): TypedMap = {
        val json = CirceUtil.decodeJsonUnsafe[Json](testElement, "invalid test data")
        JsonToTypedMapConverter.jsonToTypedMap(json)
      }
    }
  }
}

object PostGenericSourceFactory {
  val ResponseAggregationProperty = "responseAggregation"
  val WithResponseIdProperty = "withResponseId"
  val RootNameProperty = "rootName"
  val GenerateOpenApiDocsProperty = "generateOpenApiDocs"
}
