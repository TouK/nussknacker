package pl.touk.nussknacker.ui.api.description

import io.circe.Encoder
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.graph.{ProcessProperties, ScenarioGraph}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.definition.test.TestingCapabilities
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.restmodel.definition.UISourceParameters
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeValidationError, NodeValidationErrorType}
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.api.TapirCodecs.ScenarioGraphCodec._
import pl.touk.nussknacker.ui.api.TapirCodecs.ScenarioNameCodec._
import pl.touk.nussknacker.ui.api.TapirCodecs.ScenarioTestingCodecs._
import pl.touk.nussknacker.ui.api.TestingApiHttpService.Examples.{noScenarioErrorOutput, noScenarioExample}
import pl.touk.nussknacker.ui.api.TestingApiHttpService.TestingError
import pl.touk.nussknacker.ui.api.TestingApiHttpService.TestingError.BadRequestTestingError.{
  TooManyCharactersGenerated,
  TooManySamplesRequested
}
import pl.touk.nussknacker.ui.api.TestingApiHttpService.TestingError.{BadRequestTestingError, NotFoundTestingError}
import pl.touk.nussknacker.ui.api.TestingApiHttpService.TestingError.NotFoundTestingError.{
  NoDataGenerated,
  NoSourcesWithTestDataGeneration
}
import pl.touk.nussknacker.ui.definition.DefinitionsService
import sttp.model.StatusCode.{BadRequest, NotFound, Ok}
import sttp.tapir.EndpointIO.Example
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody

class TestingApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {
  import NodesApiEndpoints.Dtos._

  lazy val encoder: Encoder[TypingResult] = TypingResult.encoder

  lazy val scenarioTestingAdhocValidateEndpoint: SecuredEndpoint[
    (ProcessName, AdhocTestParametersRequest),
    TestingError,
    ParametersValidationResultDto,
    Any
  ] = {
    baseNuApiEndpoint
      .summary("Validate adhoc parameters")
      .tag("Testing")
      .post
      .in("scenarioTesting" / path[ProcessName]("scenarioName") / "adhoc" / "validate")
      .in(
        jsonBody[AdhocTestParametersRequest]
          .example(
            Example.of(
              summary = Some("Valid example of minimalistic request"),
              value = AdhocTestParametersRequest(
                TestSourceParameters("source", Map(ParameterName("name") -> Expression.spel("'Amadeus'"))),
                ScenarioGraph(
                  ProcessProperties(StreamMetaData()),
                  List(),
                  List(),
                )
              )
            )
          )
      )
      .out(
        statusCode(Ok).and(
          jsonBody[ParametersValidationResultDto]
            .examples(
              List(
                Example.of(
                  summary = Some("Validate correct parameters"),
                  value = ParametersValidationResultDto(
                    validationErrors = List.empty,
                    validationPerformed = true
                  )
                ),
                Example.of(
                  summary = Some("Validate incorrect parameters"),
                  value = ParametersValidationResultDto(
                    List(
                      NodeValidationError(
                        "ExpressionParserCompilationError",
                        "Failed to parse expression: Bad expression type, expected: Boolean, found: Long(5)",
                        "There is problem with expression in field Some(condition) - it could not be parsed.",
                        Some("condition"),
                        NodeValidationErrorType.SaveAllowed,
                        details = None
                      )
                    ),
                    validationPerformed = true
                  ),
                )
              )
            )
        )
      )
      .errorOut(
        oneOf[TestingError](
          noScenarioErrorOutput,
        )
      )
      .withSecurity(auth)
  }

  lazy val scenarioTestingCapabilitiesEndpoint
      : SecuredEndpoint[(ProcessName, ScenarioGraph), TestingError, TestingCapabilities, Any] = {
    baseNuApiEndpoint
      .summary("Describes available testing actions")
      .tag("Testing")
      .post
      .in("scenarioTesting" / path[ProcessName]("scenarioName") / "capabilities")
      .in(
        jsonBody[ScenarioGraph]
          .example(simpleGraphExample)
      )
      .out(
        statusCode(Ok).and(
          jsonBody[TestingCapabilities]
            .examples(
              List(
                Example.of(
                  summary = Some("Valid TestingCapabilities for given scenario"),
                  value = TestingCapabilities(
                    canBeTested = false,
                    canGenerateTestData = false,
                    canTestWithForm = false
                  )
                )
              )
            )
        )
      )
      .errorOut(
        oneOf[TestingError](
          noScenarioErrorOutput
        )
      )
      .withSecurity(auth)
  }

  lazy val scenarioTestingParametersEndpoint
      : SecuredEndpoint[(ProcessName, ScenarioGraph), TestingError, List[UISourceParameters], Any] = {
    baseNuApiEndpoint
      .summary("Prepare scenario input parameters")
      .tag("Testing")
      .post
      .in("scenarioTesting" / path[ProcessName]("scenarioName") / "parameters")
      .in(
        jsonBody[ScenarioGraph]
          .example(simpleGraphExample)
      )
      .out(
        statusCode(Ok).and(
          jsonBody[List[UISourceParameters]]
            .examples(
              List(
                Example.of(
                  summary = Some("Valid TestingCapabilities for given scenario"),
                  value = List(
                    UISourceParameters(
                      "source",
                      parameters = List(
                        DefinitionsService.createUIParameter(Parameter(ParameterName("name"), Typed[String]))
                      )
                    )
                  )
                )
              )
            )
        )
      )
      .errorOut(
        oneOf[TestingError](
          noScenarioErrorOutput
        )
      )
      .withSecurity(auth)
  }

  lazy val scenarioTestingGenerateEndpoint
      : SecuredEndpoint[(ProcessName, Int, ScenarioGraph), TestingError, String, Any] = {
    baseNuApiEndpoint
      .summary("Generate testing data for scenario")
      .tag("Testing")
      .post
      .in("scenarioTesting" / path[ProcessName]("scenarioName") / "generate" / path[Int]("testSampleSize"))
      .in(
        jsonBody[ScenarioGraph]
          .example(simpleGraphExample)
      )
      .out(
        statusCode(Ok).and(
          stringBody
            .examples(
              List(
                Example.of(
                  summary = Some("Simple scenario test data in json stringify form"),
                  value = "{name: John}"
                )
              )
            )
        )
      )
      .errorOut(
        oneOf[TestingError](
          oneOfVariantFromMatchType[NotFoundTestingError](
            NotFound,
            plainBody[NotFoundTestingError]
              .examples(
                List(
                  noScenarioExample,
                  Example.of(
                    summary = Some("No data was generated"),
                    value = NoDataGenerated
                  ),
                  Example.of(
                    summary = Some("No sources with test data generation available"),
                    value = NoSourcesWithTestDataGeneration
                  )
                )
              )
          ),
          oneOfVariantFromMatchType[BadRequestTestingError](
            BadRequest,
            plainBody[BadRequestTestingError]
              .examples(
                List(
                  Example.of(
                    summary = Some("Too many characters were generated"),
                    value = TooManyCharactersGenerated(length = 5000, limit = 2000)
                  ),
                  Example.of(
                    summary = Some("Too many samples requested"),
                    value = TooManySamplesRequested(maxSamples = 1000)
                  )
                )
              )
          )
        )
      )
      .withSecurity(auth)
  }

  private val simpleGraphExample: Example[ScenarioGraph] = Example.of(
    ScenarioGraph(
      ProcessProperties(StreamMetaData()),
      List(),
      List(),
    )
  )

}
