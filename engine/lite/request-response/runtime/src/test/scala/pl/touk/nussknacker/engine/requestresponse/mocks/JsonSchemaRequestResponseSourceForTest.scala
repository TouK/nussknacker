package pl.touk.nussknacker.engine.requestresponse.mocks

import io.circe.Json
import org.everit.json.schema.Validator
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.{CirceUtil, MetaData, MethodToInvoke, NodeId, ParamName, VariableConstants}
import pl.touk.nussknacker.engine.api.process.SourceTestSupport
import pl.touk.nussknacker.engine.api.test.TestDataParser
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.typed.{ReturningType, TypedMap, typing}
import pl.touk.nussknacker.engine.requestresponse.api.openapi.OpenApiSourceDefinition
import pl.touk.nussknacker.engine.requestresponse.api.{RequestResponsePostSource, RequestResponseSourceFactory, ResponseEncoder}
import pl.touk.nussknacker.engine.requestresponse.utils.QueryStringTestDataParser

/*
* Similar to JsonSchemaRequestResponseSource source to test OpenApi generator
* */
class JsonSchemaRequestResponseSourceTestFactory extends RequestResponseSourceFactory {

  @MethodToInvoke
  def create(@ParamName("inputSchema") schemaDefinition: String)(implicit metaData: MetaData, nodeId: NodeId): ContextTransformation = ContextTransformation
    .definedBy(vc => vc.withVariable(VariableConstants.InputVariableName, Typed[String], None))
    .implementedBy(new JsonSchemaRequestResponseSourceTest(schemaDefinition, metaData, nodeId))

}

class JsonSchemaRequestResponseSourceTest(val definition: String, metaData: MetaData, val nodeId: NodeId)
  extends RequestResponsePostSource[TypedMap] with ReturningType with SourceTestSupport[TypedMap] {

  protected val validator: Validator = Validator.builder().build()
  protected val openApiDescription: String = s"**scenario name**: ${metaData.id}"

  override def parse(parameters: Array[Byte]): TypedMap =  TypedMap(Map())

  private def decodeJsonWithError(str: String): Json = CirceUtil.decodeJsonUnsafe[Json](str, "Provided json is not valid")

  override def openApiDefinition: Option[OpenApiSourceDefinition] = {
    val json = decodeJsonWithError(definition)
    Option(OpenApiSourceDefinition(json, openApiDescription, List("Nussknacker")))
  }

  override def returnType: typing.TypingResult = Typed.typedClass[String]
  override def testDataParser: TestDataParser[TypedMap] = new QueryStringTestDataParser
  override def responseEncoder: Option[ResponseEncoder[TypedMap]] = None

}