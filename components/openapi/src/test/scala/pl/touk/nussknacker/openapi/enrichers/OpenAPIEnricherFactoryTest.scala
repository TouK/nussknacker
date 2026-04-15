package pl.touk.nussknacker.openapi.enrichers

import cats.data.Validated
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, OutputVariableNameValue}
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.util.service.EagerServiceWithErrorSupport.HandleErrorsParamName
import pl.touk.nussknacker.openapi.BaseOpenAPITest
import pl.touk.nussknacker.openapi.discovery.OpenApiDefinitionDiscovery
import pl.touk.nussknacker.openapi.enrichers.OpenAPIEnricherFactory.ServiceParamName
import pl.touk.nussknacker.openapi.parser.ServiceParseError
import sttp.client3.testing.SttpBackendStub

import scala.concurrent.ExecutionContext

class OpenAPIEnricherFactoryTest extends AnyFunSuite with Matchers with BaseOpenAPITest with OptionValues {

  private val service           = parseServicesFromResourceUnsafe("custom-codes.yml").head
  private val codeParameterName = ParameterName("code")

  private val openApiDefinitionDiscovery = new OpenApiDefinitionDiscovery {
    override def getServices(
        openAPIsConfig: pl.touk.nussknacker.openapi.OpenAPIServicesConfig
    ): List[Validated[ServiceParseError, pl.touk.nussknacker.openapi.SwaggerService]] =
      List(Validated.Valid(service))
  }

  private val backend = SttpBackendStub.asynchronousFuture.whenAnyRequest.thenRespondOk()

  private val factory = new OpenAPIEnricherFactory(
    config = baseConfig,
    httpBeProvider = (_: ExecutionContext) => backend,
    openApiDefinitionDiscovery = openApiDefinitionDiscovery
  )

  private implicit val nodeId: NodeId = NodeId("nodeId")

  test("should expose handleErrors parameter with default value") {
    val definition = factory.contextTransformation(ValidationContext.empty, List(OutputVariableNameValue("out")))

    val initStep = definition(factory.TransformationStep(Nil, None)).asInstanceOf[factory.NextParameters]

    val serviceSelectedStep = definition(
      factory.TransformationStep(
        List(ServiceParamName -> DefinedEagerParameter(service.name.value, Typed[String])),
        initStep.state
      )
    ).asInstanceOf[factory.NextParameters]

    val handleErrorsParameter = serviceSelectedStep.parameters.find(_.name == HandleErrorsParamName).value

    handleErrorsParameter.defaultValue shouldBe Some(Expression.spel("false"))
    handleErrorsParameter.labelOpt shouldBe Some("handleErrors")
    handleErrorsParameter.editors shouldBe List(
      FixedValuesParameterEditor(
        List(
          FixedExpressionValue("false", "false"),
          FixedExpressionValue("true", "true")
        )
      )
    )
  }

  test("should adjust output type when handleErrors changes") {
    val definition = factory.contextTransformation(ValidationContext.empty, List(OutputVariableNameValue("out")))

    val initStep = definition(factory.TransformationStep(Nil, None)).asInstanceOf[factory.NextParameters]

    val serviceSelectedStep = definition(
      factory.TransformationStep(
        List(ServiceParamName -> DefinedEagerParameter(service.name.value, Typed[String])),
        initStep.state
      )
    ).asInstanceOf[factory.NextParameters]

    val noErrorHandling = definition(
      factory.TransformationStep(
        List(
          codeParameterName     -> DefinedEagerParameter(200, Typed[Int]),
          HandleErrorsParamName -> DefinedEagerParameter(false, Typed[Boolean])
        ),
        serviceSelectedStep.state
      )
    ).asInstanceOf[factory.FinalResults]

    val withErrorHandling = definition(
      factory.TransformationStep(
        List(
          codeParameterName     -> DefinedEagerParameter(200, Typed[Int]),
          HandleErrorsParamName -> DefinedEagerParameter(true, Typed[Boolean])
        ),
        serviceSelectedStep.state
      )
    ).asInstanceOf[factory.FinalResults]

    val baseResponseType = service.responseSwaggerType.map(_.typingResult).getOrElse(Typed[Unit])
    val wrappedResponseType = Typed.record(
      Map(
        "error"           -> Typed[Boolean],
        "errorResponse"   -> Typed[String],
        "statusCode"      -> Typed[java.lang.Integer],
        "successResponse" -> baseResponseType
      )
    )

    noErrorHandling.finalContext.get("out").value shouldBe baseResponseType
    withErrorHandling.finalContext.get("out").value shouldBe wrappedResponseType
  }

}
