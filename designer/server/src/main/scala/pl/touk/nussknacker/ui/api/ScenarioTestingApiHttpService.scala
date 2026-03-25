package pl.touk.nussknacker.ui.api

import cats.data.{EitherT, NonEmptyList}
import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.stream.scaladsl.Source
import pl.touk.nussknacker.engine.api.{NodeId, NodeName}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}
import pl.touk.nussknacker.engine.test.testcase.TestCase
import pl.touk.nussknacker.restmodel.definition.UISourceParameters
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.restmodel.validation.{PrettyValidationErrors, ValidationResults}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationErrors
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.security.Permission.Permission
import pl.touk.nussknacker.ui.api.BaseHttpService.CustomAuthorizationError
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.ParametersValidationResultDto
import pl.touk.nussknacker.ui.api.description.scenarioTesting.{
  ResultsWithCountsDto,
  ResultsWithCountsDtoCodecs,
  ScenarioTestingApiEndpoints
}
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.{ScenarioTestData, TestingError}
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Capabilities.{
  CapabilityStatus,
  NotAvailableReason,
  ScenarioTestCapabilities
}
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Capabilities.TestCapabilityDetails.TestWithParametersDetails
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Test.{
  PerformTestRequestJsonBody,
  PerformTestRequestMultiParts,
  SkipResultsPerNode,
  SkipResultsPerTransition
}
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Test.PerformMultipleTestCasesResponse.TestCaseResultEvent
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.TestingError._
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.TestingError.BadRequestTestingError._
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.TestingError.NotFoundTestingError._
import pl.touk.nussknacker.ui.api.utils.ScenarioHttpServiceExtensions
import pl.touk.nussknacker.ui.definition.DefinitionsService
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.test.{ResultsWithCounts, ScenarioTestService, SerializedScenarioRecordsContent}
import pl.touk.nussknacker.ui.process.test.PreliminaryScenarioRecordsSerDe.SerializationError
import pl.touk.nussknacker.ui.process.test.ScenarioTestService.{
  FetchLiveDataError,
  ParametersDefinitionError,
  TestingCapabilitiesError
}
import pl.touk.nussknacker.ui.process.test.ScenarioTestService.PerformTestError.ScenarioNodeValidationErrors
import pl.touk.nussknacker.ui.security.api.{AuthManager, LoggedUser}
import pl.touk.nussknacker.ui.validation.ParametersValidator
import sttp.model.sse.ServerSentEvent

import scala.concurrent.{ExecutionContext, Future}

object ScenarioTestingApiHttpService {

  private[api] def filterNodeNamesById(
      nodesWithErrors: NonEmptyList[(NodeId, NonEmptyList[ProcessCompilationError])],
      nodeNamesById: Map[NodeId, String]
  ): Map[NodeId, String] = {
    val nodeIdsWithErrors = nodesWithErrors.map(_._1).toList.toSet
    nodeNamesById.filter { case (nodeId, _) => nodeIdsWithErrors.contains(nodeId) }
  }

}

class ScenarioTestingApiHttpService(
    authManager: AuthManager,
    scenarioAuthorizer: AuthorizeProcess,
    processingTypeToParametersValidator: ProcessingTypeDataProvider[ParametersValidator, _],
    processingTypeToScenarioTestServices: ProcessingTypeDataProvider[ScenarioTestService, _],
    protected override val scenarioService: ProcessService,
    multipleTestCasesEnabled: Boolean,
)(override protected implicit val executionContext: ExecutionContext)
    extends BaseHttpService(authManager)
    with ScenarioHttpServiceExtensions
    with LazyLogging {

  override protected type BusinessErrorType = TestingError // TODO make sure what type we want here
  override protected def noScenarioError(scenarioName: ProcessName): TestingError      = NoScenario(scenarioName)
  override protected def noPermissionError: TestingError with CustomAuthorizationError = NoPermission

  private val scenarioTestingApiEndpoints = new ScenarioTestingApiEndpoints(authManager.authenticationEndpointInput())

  expose {
    scenarioTestingApiEndpoints.scenarioTestCapabilitiesEndpoint
      .serverSecurityLogic(authorizeKnownUser[TestingError])
      .serverLogicEitherT { implicit loggedUser =>
        { case (scenarioName, scenarioGraph) =>
          for {
            scenarioWithDetails <- getScenarioWithDetailsByName(scenarioName)
            processId <- EitherT.fromOption[Future](
              scenarioWithDetails.processId,
              noScenarioError(scenarioName),
            )
            scenarioTestService = processingTypeToScenarioTestServices.forProcessingTypeUnsafe(
              scenarioWithDetails.processingType
            )
            capabilities = scenarioTestService.getTestingCapabilities(
              scenarioGraph,
              scenarioWithDetails.processVersionUnsafe,
            )
            canDeploy <- EitherT.right(scenarioAuthorizer.check(processId, Permission.Deploy, loggedUser))
            result = capabilities match {
              case Left(TestingCapabilitiesError.NoSourcesError) =>
                val noSourcesStatus = CapabilityStatus.NotAvailable(NotAvailableReason.NoSources)
                ScenarioTestCapabilities(noSourcesStatus, noSourcesStatus, noSourcesStatus)
              case Left(TestingCapabilitiesError.SourcesCompilationError(_)) =>
                val invalidScenarioStatus = CapabilityStatus.NotAvailable(NotAvailableReason.InvalidScenario)
                ScenarioTestCapabilities(invalidScenarioStatus, invalidScenarioStatus, invalidScenarioStatus)
              case Right(capabilities) =>
                val testWithLiveDataResult =
                  (canDeploy, capabilities.canBeTested && capabilities.canFetchLiveData) match {
                    case (false, _) =>
                      CapabilityStatus.NotAvailable(NotAvailableReason.UserDoesNotHavePermission)
                    case (true, false) =>
                      CapabilityStatus.NotAvailable(NotAvailableReason.NotSupportedBySources)
                    case (true, true) =>
                      CapabilityStatus.available
                  }
                ScenarioTestCapabilities(
                  testWithParameters = {
                    (canDeploy, capabilities.canTestWithForm) match {
                      case (false, _) =>
                        CapabilityStatus.NotAvailable(NotAvailableReason.UserDoesNotHavePermission)
                      case (true, false) =>
                        CapabilityStatus.NotAvailable(NotAvailableReason.NotSupportedBySources)
                      case (true, true) =>
                        scenarioTestService.validateAndGetTestParametersDefinition(
                          scenarioGraph,
                          scenarioWithDetails.processVersionUnsafe,
                          scenarioWithDetails.isFragment
                        ) match {
                          case Right(parameters) =>
                            val nodeNamesById = scenarioGraph.nodes.map(n => n.id -> n.name).toMap
                            val uiParameters = parameters.map { case (id, params) =>
                              val nodeName = nodeNamesById
                                .get(id)
                                .fold {
                                  logger.warn(s"Missing source node name for id: ${id.value}")
                                  "Unknown node name"
                                }(_.value)
                              UISourceParameters(
                                id.value,
                                nodeName,
                                params.map(DefinitionsService.createUIParameter)
                              )
                            }.toList
                            CapabilityStatus.Available(TestWithParametersDetails(uiParameters))
                          case Left(ParametersDefinitionError.TestingWithCustomInputNotSupportedError(_, _)) =>
                            CapabilityStatus.NotAvailable(NotAvailableReason.NotSupportedBySources)
                          case Left(ParametersDefinitionError.SourcesCompilationError(_)) =>
                            CapabilityStatus.NotAvailable(NotAvailableReason.InvalidScenario)
                        }
                    }
                  },
                  testWithGeneratedData = testWithLiveDataResult,
                  testWithLiveData = testWithLiveDataResult,
                )
            }
          } yield result
        }
      }
  }

  expose {
    scenarioTestingApiEndpoints.scenarioTestEndpoint
      .serverSecurityLogic(authorizeKnownUser[TestingError])
      .serverLogicEitherT { implicit loggedUser =>
        { case (scenarioName, request, skipResultsPerNode, skipResultsPerTransition) =>
          for {
            scenarioWithDetails <- getScenarioWithDetailsByName(scenarioName)
            processId <- EitherT
              .fromOption[Future](scenarioWithDetails.processId, noScenarioError(scenarioName): TestingError)
            _ <- isAuthorized(processId, Permission.Deploy)
            scenarioTestService = processingTypeToScenarioTestServices.forProcessingTypeUnsafe(
              scenarioWithDetails.processingType
            )
            resultWithCounts <- request match {
              case PerformTestRequestJsonBody(scenarioGraph, testData) =>
                performTest(
                  scenarioName,
                  scenarioGraph,
                  scenarioWithDetails,
                  scenarioTestService,
                  testData
                )
              case PerformTestRequestMultiParts(scenarioGraph, testData) =>
                EitherT(
                  scenarioTestService.performTest(
                    scenarioGraph,
                    scenarioWithDetails.processVersionUnsafe,
                    scenarioWithDetails.isFragment,
                    SerializedScenarioRecordsContent(testData)
                  )
                ).leftMap[TestingError] { error =>
                  ErrorResult(TestingApiErrorMessages.from(error))
                }
            }
          } yield {
            ResultsWithCountsDto.from(
              resultWithCounts,
              skipResultsPerNode,
              skipResultsPerTransition,
            )
          }
        }
      }
  }

  expose {
    scenarioTestingApiEndpoints.scenarioTestValidationEndpoint
      .serverSecurityLogic(authorizeKnownUser[TestingError])
      .serverLogicEitherT { implicit loggedUser =>
        { case (scenarioName, request) =>
          for {
            scenarioWithDetails <- getScenarioWithDetailsByName(scenarioName)
            scenarioTestService = processingTypeToScenarioTestServices.forProcessingTypeUnsafe(
              scenarioWithDetails.processingType
            )
            validationResults <- validateTestData(
              scenarioName = scenarioName,
              scenarioGraph = request.scenarioGraph,
              scenarioWithDetails = scenarioWithDetails,
              scenarioTestService = scenarioTestService,
              scenarioTestData = request.testData,
            )
          } yield ParametersValidationResultDto(validationResults, validationPerformed = true)
        }
      }
  }

  private def performTest(
      scenarioName: ProcessName,
      scenarioGraph: ScenarioGraph,
      scenarioWithDetails: ScenarioWithDetails,
      scenarioTestService: ScenarioTestService,
      scenarioTestData: ScenarioTestData,
  )(implicit loggedUser: LoggedUser): EitherT[Future, TestingError, ResultsWithCounts] = {
    for {
      validationErrors <- validateTestData(
        scenarioName = scenarioName,
        scenarioGraph = scenarioGraph,
        scenarioWithDetails = scenarioWithDetails,
        scenarioTestService = scenarioTestService,
        scenarioTestData = scenarioTestData,
      )
      _ <- EitherT.fromEither[Future](
        Either.cond(
          test = validationErrors.isEmpty,
          right = (),
          left = ErrorResult(TestingApiErrorMessages.from(ScenarioNodeValidationErrors(validationErrors)))
        )
      )
      result <- scenarioTestData match {
        case ScenarioTestData.WithParameters(sourceParameters) =>
          EitherT(
            scenarioTestService.performTest(
              scenarioGraph,
              scenarioWithDetails.processVersionUnsafe,
              scenarioWithDetails.isFragment,
              sourceParameters
            )
          ).leftMap[TestingError] { error =>
            ErrorResult(TestingApiErrorMessages.from(error))
          }
        case ScenarioTestData.WithLiveData(numberOfSamples) =>
          scenarioTestService.fetchSourcesLiveData(
            scenarioGraph,
            scenarioWithDetails.processVersionUnsafe,
            scenarioWithDetails.isFragment,
            numberOfSamples
          ) match {
            case Left(error) =>
              EitherT.fromEither[Future](Left(toDto(error, scenarioGraph)))
            case Right(serializedLiveData) =>
              EitherT(
                scenarioTestService
                  .performTest(
                    scenarioGraph,
                    scenarioWithDetails.processVersionUnsafe,
                    scenarioWithDetails.isFragment,
                    serializedLiveData
                  )
              ).leftMap[TestingError] { error =>
                ErrorResult(TestingApiErrorMessages.from(error))
              }
          }
      }

    } yield result
  }

  private def validateTestData(
      scenarioName: ProcessName,
      scenarioGraph: ScenarioGraph,
      scenarioWithDetails: ScenarioWithDetails,
      scenarioTestService: ScenarioTestService,
      scenarioTestData: ScenarioTestData,
  )(implicit loggedUser: LoggedUser): EitherT[Future, TestingError, List[ValidationResults.NodeValidationError]] = {
    val validator = processingTypeToParametersValidator.forProcessingTypeUnsafe(scenarioWithDetails.processingType)
    val metaData  = scenarioGraph.properties.toMetaData(scenarioName)
    scenarioTestData match {
      case ScenarioTestData.WithParameters(sourceParameters) =>
        EitherT
          .fromEither[Future](
            scenarioTestService
              .validateAndGetTestParametersDefinition(
                scenarioGraph,
                scenarioWithDetails.processVersionUnsafe,
                scenarioWithDetails.isFragment
              )
              .left
              .map(error => toDto(error, scenarioGraph))
          )
          .map(validator.validate(sourceParameters, _)(metaData))
      case ScenarioTestData.WithLiveData(numberOfSamples) =>
        EitherT
          .fromEither[Future](
            scenarioTestService.validateRecordsCount[TestingError](numberOfSamples)(
              BadRequestTestingError.TooManyRecordsRequested(_)
            )
          )
          .map((_: Unit) => List.empty)
    }
  }

  expose {
    scenarioTestingApiEndpoints.scenarioTestGeneratedDataEndpoint
      .serverSecurityLogic(authorizeKnownUser[TestingError])
      .serverLogicEitherT { implicit loggedUser =>
        { case (scenarioName, request) =>
          for {
            scenarioWithDetails <- getScenarioWithDetailsByName(scenarioName)
            scenarioTestService = processingTypeToScenarioTestServices.forProcessingTypeUnsafe(
              scenarioWithDetails.processingType
            )
            parametersDefinition <- EitherT[Future, TestingError, String](
              scenarioTestService.fetchSourcesLiveData(
                request.scenarioGraph,
                scenarioWithDetails.processVersionUnsafe,
                scenarioWithDetails.isFragment,
                request.numberOfSamples
              ) match {
                case Left(error) =>
                  logger.info(s"Could not generate test data: $error")
                  Future(Left(toDto(error, request.scenarioGraph)))
                case Right(serializedLiveData) =>
                  Future(Right(serializedLiveData.content))
              }
            )
          } yield parametersDefinition
        }
      }
  }

  expose {
    scenarioTestingApiEndpoints.scenarioTestCaseEndpoint
      .serverSecurityLogic(authorizeKnownUser[TestingError])
      .serverLogicEitherT { implicit loggedUser =>
        { case (scenarioName, request, skipResultsPerNode, skipResultsPerTransition) =>
          for {
            scenarioWithDetails <- getScenarioWithDetailsByName(scenarioName)
            processId <- EitherT
              .fromOption[Future](scenarioWithDetails.processId, noScenarioError(scenarioName): TestingError)
            _ <- isAuthorized(processId, Permission.Deploy)
            resultWithCounts <- EitherT(
              processingTypeToScenarioTestServices
                .forProcessingTypeUnsafe(scenarioWithDetails.processingType)
                .performTestCase(
                  request.scenarioGraph,
                  scenarioWithDetails.processVersionUnsafe,
                  scenarioWithDetails.isFragment,
                  request.testCase,
                )
            ).leftMap[TestingError] { error =>
              ErrorResult(TestingApiErrorMessages.from(error))
            }
          } yield {
            ResultsWithCountsDto.from(
              resultWithCounts,
              skipResultsPerNode,
              skipResultsPerTransition,
            )
          }
        }
      }
  }

  expose(when = multipleTestCasesEnabled) {
    scenarioTestingApiEndpoints.scenarioMultipleTestCasesEndpoint
      .serverSecurityLogic(authorizeKnownUser[TestingError])
      .serverLogicEitherT { implicit loggedUser =>
        { case (scenarioName, request, skipResultsPerNode, skipResultsPerTransition) =>
          for {
            scenarioWithDetails <- getScenarioWithDetailsByName(scenarioName)
            processId <- EitherT
              .fromOption[Future](scenarioWithDetails.processId, noScenarioError(scenarioName): TestingError)
            _ <- isAuthorized(processId, Permission.Deploy)
          } yield buildTestCasesStream(
            request.scenarioGraph,
            scenarioWithDetails,
            request.testCases,
            skipResultsPerNode,
            skipResultsPerTransition
          )(loggedUser)
        }
      }
  }

  private def buildTestCasesStream(
      scenarioGraph: ScenarioGraph,
      scenarioWithDetails: ScenarioWithDetails,
      testCases: NonEmptyList[TestCase],
      skipResultsPerNode: SkipResultsPerNode,
      skipResultsPerTransition: SkipResultsPerTransition,
  )(implicit loggedUser: LoggedUser): Source[TestCaseResultEvent, Any] = {
    processingTypeToScenarioTestServices
      .forProcessingTypeUnsafe(scenarioWithDetails.processingType)
      .performMultipleTestCases(
        scenarioGraph,
        scenarioWithDetails.processVersionUnsafe,
        scenarioWithDetails.isFragment,
        testCases
      )
      .map { case (testCase, result) =>
        result match {
          case Right(resultsWithCounts) =>
            TestCaseResultEvent.Success(
              testCase.id,
              testCase.name,
              ResultsWithCountsDto.from(resultsWithCounts, skipResultsPerNode, skipResultsPerTransition)
            )
          case Left(error) =>
            TestCaseResultEvent.Error(testCase.id, testCase.name, TestingApiErrorMessages.from(error))
        }
      }
  }

  private def toDto(error: ParametersDefinitionError, scenarioGraph: ScenarioGraph): TestingError = {
    val nodeNamesById = extractNodeNamesById(scenarioGraph)
    error match {
      case ParametersDefinitionError.SourcesCompilationError(nodesWithErrors) =>
        SourcesCompilationError(
          ValidationErrors.initial(invalidNodes = collectInvalidNodes(nodesWithErrors)),
          nodeNamesById = ScenarioTestingApiHttpService.filterNodeNamesById(nodesWithErrors, nodeNamesById)
        )
      case ParametersDefinitionError.TestingWithCustomInputNotSupportedError(nodeId, nodeName) =>
        BadRequestTestingError.TestingWithCustomInputNotSupportedError(nodeId, nodeName)
    }
  }

  private def toDto(error: FetchLiveDataError, scenarioGraph: ScenarioGraph): TestingError = {
    val nodeNamesById = extractNodeNamesById(scenarioGraph)
    error match {
      case FetchLiveDataError.SourcesCompilationError(nodesWithErrors) =>
        SourcesCompilationError(
          ValidationErrors.initial(invalidNodes = collectInvalidNodes(nodesWithErrors)),
          nodeNamesById = ScenarioTestingApiHttpService.filterNodeNamesById(nodesWithErrors, nodeNamesById)
        )
      case FetchLiveDataError.NoLiveDataAvailableError =>
        NoLiveDataAvailable
      case FetchLiveDataError.LiveDataFetchingNotSupportedError =>
        NoSourcesWithLiveDataFetchingSupport
      case FetchLiveDataError.ScenarioRecordsSerializationError(cause) =>
        cause match {
          case SerializationError.TooManyCharactersGenerated(length, limit) =>
            TooManyCharactersGenerated(length, limit)
        }
      case FetchLiveDataError.TooManyRecordsRequestedError(maxRecordsCount) =>
        TooManyRecordsRequested(maxRecordsCount)
    }
  }

  private def extractNodeNamesById(scenarioGraph: ScenarioGraph): Map[NodeId, String] =
    scenarioGraph.nodes.map(node => node.id -> node.name.value).toMap

  private def collectInvalidNodes(
      nodesWithErrors: NonEmptyList[(NodeId, NonEmptyList[ProcessCompilationError])]
  ): Map[NodeId, List[ValidationResults.NodeValidationError]] = {
    nodesWithErrors
      .map { case (nodeId, errors) =>
        (nodeId, errors.map(PrettyValidationErrors.formatErrorMessage).toList)
      }
      .toList
      .toMap
  }

  private def isAuthorized(scenarioId: ProcessId, permission: Permission)(
      implicit loggedUser: LoggedUser
  ): EitherT[Future, TestingError, Unit] =
    EitherT(
      scenarioAuthorizer
        .check(scenarioId, permission, loggedUser)
        .map[Either[TestingError, Unit]] {
          case true  => Right(())
          case false => Left(noPermissionError)
        }
    )

}
