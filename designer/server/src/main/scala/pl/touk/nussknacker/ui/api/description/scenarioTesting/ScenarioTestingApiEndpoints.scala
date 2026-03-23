package pl.touk.nussknacker.ui.api.description.scenarioTesting

import org.apache.pekko.stream.scaladsl.Source
import pl.touk.nussknacker.engine.api.{NodeId, StreamMetaData}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.ExpressionParserCompilationError
import pl.touk.nussknacker.engine.api.definition.{MandatoryParameterValidator, Parameter, SpelParameterEditor}
import pl.touk.nussknacker.engine.api.graph.{ProcessProperties, ScenarioGraph}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.restmodel.definition.UISourceParameters
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{
  NodeValidationError,
  NodeValidationErrorType,
  ValidationErrors
}
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.api.TapirCodecs.ScenarioNameCodec._
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.{
  ParametersValidationResultDto,
  TestSourceParameters
}
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Capabilities.{
  CapabilityStatus,
  ScenarioTestCapabilities
}
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Capabilities.TestCapabilityDetails.TestWithParametersDetails
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.LiveDataFetching.FetchSourcesLiveDataRequest
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Test.{
  PerformMultipleTestCasesRequest,
  PerformTestCaseRequest,
  PerformTestRequest,
  PerformTestRequestJsonBody,
  PerformTestRequestMultiParts,
  SkipResultsPerNode,
  SkipResultsPerTransition
}
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Test.PerformTestCaseRequest._
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.TestingError.{
  BadRequestTestingError,
  NotFoundTestingError
}
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.TestingError.BadRequestTestingError.{
  SourcesCompilationError,
  TooManyCharactersGenerated,
  TooManyRecordsRequested
}
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.TestingError.NotFoundTestingError.{
  NoLiveDataAvailable,
  NoScenario,
  NoSourcesWithLiveDataFetchingSupport
}
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Validate.ScenarioTestValidationRequest
import pl.touk.nussknacker.ui.definition.DefinitionsService
import sttp.capabilities.pekko.PekkoStreams
import sttp.model.StatusCode.{BadRequest, NotFound, Ok}
import sttp.model.sse.ServerSentEvent
import sttp.tapir._
import sttp.tapir.EndpointIO.Example
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.pekkohttp.serverSentEventsBody

class ScenarioTestingApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  import Dtos._
  import ResultsWithCountsDtoCodecs._

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
      .in("scenarioTesting" / path[ProcessName]("scenarioName") / "capabilities")
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
                            "513eab33-d9b3-4c67-a9e7-b84ae184eb64",
                            "source",
                            List(
                              DefinitionsService.createUIParameter(
                                Parameter(
                                  name = ParameterName("name"),
                                  typ = Typed[String],
                                  validators = List(MandatoryParameterValidator),
                                  editors = List(SpelParameterEditor)
                                )
                              )
                            )
                          )
                        )
                      )
                    ),
                    testWithGeneratedData = CapabilityStatus.available,
                    testWithLiveData = CapabilityStatus.available,
                  )
                )
              )
            )
        )
      )
      .errorOut(testingErrorOutput)
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
      .in("scenarioTesting" / path[ProcessName]("scenarioName") / "validate")
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
                        "Bad expression type, expected: Boolean, found: Long(5)",
                        "There is problem with expression in field [condition] - it could not be parsed.",
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
      .errorOut(testingErrorOutput)
      .withSecurity(auth)
  }

  def scenarioTestEndpoint: SecuredEndpoint[
    (
        ProcessName,
        PerformTestRequest,
        SkipResultsPerNode,
        SkipResultsPerTransition,
    ),
    TestingError,
    ResultsWithCountsDto,
    Any
  ] = {
    import PerformTestRequestMultiParts._

    baseNuApiEndpoint
      .summary("Perform test")
      .tag("Testing")
      .post
      .in("scenarioTesting" / path[ProcessName]("scenarioName") / "performTest")
      .in(
        oneOfBody[PerformTestRequest](
          jsonBody[PerformTestRequestJsonBody].map[PerformTestRequest]((x: PerformTestRequestJsonBody) =>
            x: PerformTestRequest
          ) {
            case jsonBody: PerformTestRequestJsonBody => jsonBody
            case other => throw new IllegalArgumentException(s"$other should not be used as endpoint output body")
          },
          multipartBody[PerformTestRequestMultiParts].map[PerformTestRequest]((x: PerformTestRequestMultiParts) =>
            x: PerformTestRequest
          ) {
            case multiParts: PerformTestRequestMultiParts => multiParts
            case other => throw new IllegalArgumentException(s"$other should not be used as endpoint output body")
          },
        )
      )
      .in(skipResultsPerNodeQueryParam)
      .in(skipResultsPerTransitionQueryParam)
      .out(statusCode(Ok).and(jsonBody[ResultsWithCountsDto]))
      .errorOut(testingErrorOutput)
      .withSecurity(auth)
  }

  def scenarioTestCaseEndpoint: SecuredEndpoint[
    (
        ProcessName,
        PerformTestCaseRequest,
        SkipResultsPerNode,
        SkipResultsPerTransition,
    ),
    TestingError,
    ResultsWithCountsDto,
    Any
  ] =
    baseNuApiEndpoint
      .summary("Perform single test case")
      .tag("Testing")
      .post
      .in("scenarioTesting" / path[ProcessName]("scenarioName") / "performTestCase")
      .in(jsonBody[PerformTestCaseRequest])
      .in(skipResultsPerNodeQueryParam)
      .in(skipResultsPerTransitionQueryParam)
      .out(statusCode(Ok).and(jsonBody[ResultsWithCountsDto]))
      .errorOut(testingErrorOutput)
      .withSecurity(auth)

  def scenarioPerformTestCasesEndpoint: SecuredEndpoint[
    (ProcessName, PerformMultipleTestCasesRequest),
    TestingError,
    Source[ServerSentEvent, Any],
    Any with PekkoStreams
  ] =
    baseNuApiEndpoint
      .summary("Perform multiple test cases with streaming results")
      .tag("Testing")
      .post
      .in("scenarioTesting" / path[ProcessName]("scenarioName") / "performTestCases")
      .in(jsonBody[PerformMultipleTestCasesRequest])
      .out(serverSentEventsBody)
      .errorOut(testingErrorOutput)
      .withSecurity(auth)

  implicit def skipResultsPerNodeQueryParam: EndpointInput.Query[SkipResultsPerNode] =
    query[Boolean]("skipResultsPerNode")
      .default(false)
      .map(SkipResultsPerNode(_))(_.value)

  private def skipResultsPerTransitionQueryParam =
    query[Boolean]("skipResultsPerTransition")
      .default(false)
      .map(SkipResultsPerTransition(_))(_.value)

  def scenarioTestGeneratedDataEndpoint: SecuredEndpoint[
    (ProcessName, FetchSourcesLiveDataRequest),
    TestingError,
    String,
    Any
  ] = {
    baseNuApiEndpoint
      .summary("Generate testing data for scenario")
      .tag("Testing")
      .post
      .in("scenarioTesting" / path[ProcessName]("scenarioName") / "generatedTestData")
      .in(jsonBody[FetchSourcesLiveDataRequest])
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
      .errorOut(testingErrorOutput)
      .withSecurity(auth)
  }

  private val testingErrorOutput = oneOf[TestingError](
    oneOfVariantFromMatchType[NotFoundTestingError](
      NotFound,
      plainBody[NotFoundTestingError]
        .examples(
          List(
            Example.of(
              summary = Some("No scenario {scenarioName} found"),
              value = NoScenario(ProcessName("'example scenario'"))
            ),
            Example.of(
              summary = Some("No live test data available"),
              value = NoLiveDataAvailable
            ),
            Example.of(
              summary = Some("No sources with test data generation available"),
              value = NoSourcesWithLiveDataFetchingSupport
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
              summary = Some("Too many records requested"),
              value = TooManyRecordsRequested(maxRecordsCount = 1000)
            ),
            Example.of(
              summary = Some("Scenario validation error"),
              value = SourcesCompilationError(
                ValidationErrors(
                  invalidNodes = Map(
                    NodeId("source") -> List(
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
                  processPropertiesErrors = List.empty,
                  testCasesValidationErrors = None,
                ),
                nodeNamesById = Map(NodeId("source") -> "source")
              )
            )
          )
        )
    )
  )

  private val simpleGraphExample: Example[ScenarioGraph] = Example.of(
    ScenarioGraph(
      ProcessProperties(StreamMetaData()),
      List(),
      List(),
    )
  )

}
