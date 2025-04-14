package pl.touk.nussknacker.ui.api.description.scenarioTests

import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.ExpressionParserCompilationError
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.graph.{ProcessProperties, ScenarioGraph}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.api.{NodeId, StreamMetaData}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.restmodel.definition.UISourceParameters
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeValidationError, NodeValidationErrorType, ValidationErrors}
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.api.ScenarioTestApiHttpService.Examples.{noScenarioErrorOutput, noScenarioExample}
import pl.touk.nussknacker.ui.api.ScenarioTestApiHttpService.TestingError
import pl.touk.nussknacker.ui.api.ScenarioTestApiHttpService.TestingError.BadRequestTestingError.{ScenarioGraphValidationError, TooManyCharactersGenerated, TooManySamplesRequested}
import pl.touk.nussknacker.ui.api.ScenarioTestApiHttpService.TestingError.NotFoundTestingError.{NoDataGenerated, NoSourcesWithTestDataGeneration}
import pl.touk.nussknacker.ui.api.ScenarioTestApiHttpService.TestingError.{BadRequestTestingError, NotFoundTestingError}
import pl.touk.nussknacker.ui.api.TapirCodecs.ScenarioNameCodec._
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.{ParametersValidationResultDto, TestSourceParameters}
import pl.touk.nussknacker.ui.api.description.scenarioTests.Dtos.Capabilities.TestCapabilityDetails.TestWithParametersDetails
import pl.touk.nussknacker.ui.api.description.scenarioTests.Dtos.Capabilities.{CapabilityStatus, ScenarioTestCapabilities}
import pl.touk.nussknacker.ui.api.description.scenarioTests.Dtos.GeneratedTestData.GeneratedTestDataRequest
import pl.touk.nussknacker.ui.api.description.scenarioTests.Dtos.Test.PerformTestRequest
import pl.touk.nussknacker.ui.api.description.scenarioTests.Dtos.Test.PerformTestRequest._
import pl.touk.nussknacker.ui.api.description.scenarioTests.Dtos.Validate.ScenarioTestValidationRequest
import pl.touk.nussknacker.ui.definition.DefinitionsService
import pl.touk.nussknacker.ui.process.test.ResultsWithCounts
import sttp.model.StatusCode.{BadRequest, NotFound, Ok}
import sttp.tapir.EndpointIO.Example
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody

class ScenarioTestApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  import TestResultsCodecs._
  import Dtos._

  def scenarioTestCapabilitiesEndpoint: SecuredEndpoint[
    (ProcessName, ScenarioGraph),
    TestingError,
    ScenarioTestCapabilities,
    Any
  ] =
    baseNuApiEndpoint
      .summary("Describes available test modes")
      .tag("Testing")
      .post
      .in("scenarioTest" / path[ProcessName]("scenarioName") / "capabilities")
      .in(
        jsonBody[ScenarioGraph]
          .example(simpleGraphExample)
      )
      .out(
        statusCode(Ok).and(
          jsonBody[ScenarioTestCapabilities]
            .examples(
              List(
                Example.of(
                  summary = Some("Valid TestingCapabilities for given scenario"),
                  value = ScenarioTestCapabilities(
                    testWithParameters = CapabilityStatus.Available(
                      TestWithParametersDetails(
                        List(
                          UISourceParameters(
                            "source",
                            List(
                              DefinitionsService.createUIParameter(Parameter(ParameterName("name"), Typed[String]))
                            )
                          )
                        )
                      )
                    ),
                    testWithGeneratedData = CapabilityStatus.available,
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

  def scenarioTestValidationEndpoint: SecuredEndpoint[
    (ProcessName, ScenarioTestValidationRequest),
    TestingError,
    ParametersValidationResultDto,
    Any
  ] = {
    baseNuApiEndpoint
      .summary("Validate adhoc parameters")
      .tag("Testing")
      .post
      .in("scenarioTest" / path[ProcessName]("scenarioName") / "validate")
      .in(
        jsonBody[ScenarioTestValidationRequest]
          .example(
            Example.of(
              summary = Some("Valid example of minimalistic request"),
              value = ScenarioTestValidationRequest(
                ScenarioGraph(
                  ProcessProperties(StreamMetaData()),
                  List(),
                  List(),
                ),
                ScenarioTestData.WithParameters(
                  TestSourceParameters("source", Map(ParameterName("name") -> Expression.spel("'Amadeus'")))
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

  def scenarioTestEndpoint: SecuredEndpoint[
    (ProcessName, PerformTestRequest),
    TestingError,
    ResultsWithCounts,
    Any
  ] =
    baseNuApiEndpoint
      .summary("Perform test")
      .tag("Testing")
      .post
      .in("scenarioTest" / path[ProcessName]("scenarioName") / "test")
      .in(jsonBody[PerformTestRequest])
      .out(statusCode(Ok).and(jsonBody[ResultsWithCounts]))
      .errorOut(
        oneOf[TestingError](
          noScenarioErrorOutput
        )
      )
      .withSecurity(auth)

  def scenarioTestGeneratedDataEndpoint: SecuredEndpoint[
    (ProcessName, GeneratedTestDataRequest),
    TestingError,
    String,
    Any
  ] = {
    baseNuApiEndpoint
      .summary("Generate testing data for scenario")
      .tag("Testing")
      .post
      .in("scenarioTest" / path[ProcessName]("scenarioName") / "generatedTestData")
      .in(jsonBody[GeneratedTestDataRequest])
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
          oneOfVariant[BadRequestTestingError](
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
                  ),
                  Example.of(
                    summary = Some("Scenario validation error"),
                    value = ScenarioGraphValidationError(
                      ValidationErrors(
                        invalidNodes = Map(
                          "source" -> List(
                            PrettyValidationErrors.formatErrorMessage(
                              ExpressionParserCompilationError(
                                message = "Bad expression",
                                paramName = None,
                                originalExpr = "",
                                details = None
                              )(NodeId("source"))
                            )
                          )
                        ),
                        globalErrors = List.empty,
                        processPropertiesErrors = List.empty
                      )
                    )
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
