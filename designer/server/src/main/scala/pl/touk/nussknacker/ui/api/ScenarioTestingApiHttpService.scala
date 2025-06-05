package pl.touk.nussknacker.ui.api

import cats.data.EitherT
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationErrors
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.security.Permission.Permission
import pl.touk.nussknacker.ui.api.BaseHttpService.CustomAuthorizationError
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.ParametersValidationResultDto
import pl.touk.nussknacker.ui.api.description.scenarioTesting.{ResultsWithCountsDto, ScenarioTestingApiEndpoints}
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.{ScenarioTestData, TestingError}
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Capabilities.{
  CapabilityStatus,
  NotAvailableReason,
  ScenarioTestCapabilities,
  TestCapabilityDetails
}
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Capabilities.TestCapabilityDetails.{
  EmptyDetails,
  TestWithParametersDetails
}
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Test.{SkipResultsPerNode, SkipResultsPerTransition}
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.TestingError._
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.TestingError.BadRequestTestingError._
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.TestingError.NotFoundTestingError._
import pl.touk.nussknacker.ui.api.utils.ScenarioHttpServiceExtensions
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.test.PreliminaryScenarioTestDataSerDe.SerializationError
import pl.touk.nussknacker.ui.process.test.ScenarioTestService
import pl.touk.nussknacker.ui.process.test.ScenarioTestService.FetchLiveDataError
import pl.touk.nussknacker.ui.process.test.TestInfoProvider.{
  ParametersDefinitionError,
  SourcesLiveDataFetchingError,
  TestingCapabilitiesError
}
import pl.touk.nussknacker.ui.security.api.{AuthManager, LoggedUser}
import pl.touk.nussknacker.ui.validation.ParametersValidator

import scala.concurrent.{ExecutionContext, Future}

class ScenarioTestingApiHttpService(
    authManager: AuthManager,
    scenarioAuthorizer: AuthorizeProcess,
    processingTypeToParametersValidator: ProcessingTypeDataProvider[ParametersValidator, _],
    processingTypeToScenarioTestServices: ProcessingTypeDataProvider[ScenarioTestService, _],
    protected override val scenarioService: ProcessService
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
              case Left(TestingCapabilitiesError.SourceCompilationError) =>
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
                        scenarioTestService.testUISourceParametersDefinition(
                          scenarioGraph,
                          scenarioWithDetails.processVersionUnsafe,
                        ) match {
                          case Right(parameters) =>
                            CapabilityStatus.Available(TestWithParametersDetails(parameters))
                          case Left(ParametersDefinitionError.NotSupportedBySource(_)) =>
                            CapabilityStatus.NotAvailable(NotAvailableReason.NotSupportedBySources)
                          case Left(ParametersDefinitionError.SourceValidationError(_)) =>
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
            resultWithCounts <- request.testData match {
              case ScenarioTestData.WithParameters(sourceParameters) =>
                EitherT(
                  scenarioTestService.performTest(
                    request.scenarioGraph,
                    scenarioWithDetails.processVersionUnsafe,
                    scenarioWithDetails.isFragment,
                    sourceParameters
                  )
                ).leftMap[TestingError] { error =>
                  ErrorResult(TestingApiErrorMessages.from(error))
                }
              case ScenarioTestData.WithLiveData(numberOfSamples) =>
                scenarioTestService.fetchSourcesLiveData(
                  request.scenarioGraph,
                  scenarioWithDetails.processVersionUnsafe,
                  scenarioWithDetails.isFragment,
                  numberOfSamples
                ) match {
                  case Left(error) =>
                    EitherT.fromEither[Future](Left(toDto(error)))
                  case Right(rawScenarioTestData) =>
                    EitherT(
                      scenarioTestService
                        .performTest(
                          request.scenarioGraph,
                          scenarioWithDetails.processVersionUnsafe,
                          scenarioWithDetails.isFragment,
                          rawScenarioTestData
                        )
                    ).leftMap[TestingError] { error =>
                      ErrorResult(TestingApiErrorMessages.from(error))
                    }
                }
            }
          } yield ResultsWithCountsDto.from(
            resultWithCounts,
            skipResultsPerNode.getOrElse(SkipResultsPerNode(false)),
            skipResultsPerTransition.getOrElse(SkipResultsPerTransition(false)),
          )
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
            validator = processingTypeToParametersValidator.forProcessingTypeUnsafe(scenarioWithDetails.processingType)
            scenarioTestService = processingTypeToScenarioTestServices.forProcessingTypeUnsafe(
              scenarioWithDetails.processingType
            )
            metaData = request.scenarioGraph.properties.toMetaData(scenarioName)
            validationResults <- request.testData match {
              case ScenarioTestData.WithParameters(sourceParameters) =>
                EitherT
                  .fromEither[Future](
                    scenarioTestService
                      .validateAndGetTestParametersDefinition(
                        request.scenarioGraph,
                        scenarioWithDetails.processVersionUnsafe,
                        scenarioWithDetails.isFragment
                      )
                      .left
                      .map[TestingError](error => BadRequestTestingError.UnsupportedOperation(error.message))
                  )
                  .map(validator.validate(sourceParameters, _)(metaData))
              case ScenarioTestData.WithLiveData(numberOfSamples) =>
                EitherT
                  .fromEither[Future](
                    scenarioTestService.validateSampleSize[TestingError](numberOfSamples)(
                      BadRequestTestingError.TooManySamplesRequested(_)
                    )
                  )
                  .map((_: Unit) => List.empty)
            }
          } yield ParametersValidationResultDto(validationResults, validationPerformed = true)
        }
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
                  logger.error(s"Error during generation of test data: $error")
                  Future(Left(toDto(error)))
                case Right(rawScenarioTestData) =>
                  Future(Right(rawScenarioTestData.content))
              }
            )
          } yield parametersDefinition
        }
      }
  }

  private def toDto(error: FetchLiveDataError): TestingError = {
    error match {
      case FetchLiveDataError.SourcesLiveDataFetchingError(cause) =>
        cause match {
          case SourcesLiveDataFetchingError.ScenarioGraphValidationError(nodesWithErrors) =>
            ScenarioGraphValidationError(
              ValidationErrors(
                invalidNodes = nodesWithErrors
                  .map { case (nodeId, errors) =>
                    (nodeId.id, errors.map(PrettyValidationErrors.formatErrorMessage).toList)
                  }
                  .toList
                  .toMap,
                processPropertiesErrors = List.empty,
                globalErrors = List.empty
              )
            )
          case SourcesLiveDataFetchingError.NoLiveDataAvailable =>
            NoLiveDataAvailable
          case SourcesLiveDataFetchingError.NoSourcesWithLiveDataFetchingSupport =>
            NoSourcesWithLiveDataFetchingSupport
        }
      case FetchLiveDataError.ScenarioTestDataSerializationError(cause) =>
        cause match {
          case SerializationError.TooManyCharactersGenerated(length, limit) =>
            TooManyCharactersGenerated(length, limit)
        }
      case FetchLiveDataError.TooManySamplesRequestedError(maxSamples) =>
        TooManySamplesRequested(maxSamples)
    }
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
