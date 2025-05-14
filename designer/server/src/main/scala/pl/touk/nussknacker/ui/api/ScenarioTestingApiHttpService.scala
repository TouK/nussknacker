package pl.touk.nussknacker.ui.api

import cats.data.EitherT
import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, LiveDataPreviewSupported, NoLiveDataPreviewSupport}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.definition.test.TestInfoProvider.{
  ParametersDefinitionError,
  ScenarioTestDataGenerationError,
  TestingCapabilitiesError
}
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationErrors
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.security.Permission.Permission
import pl.touk.nussknacker.ui.api.BaseHttpService.CustomAuthorizationError
import pl.touk.nussknacker.ui.api.ScenarioTestingApiHttpService.TestingError
import pl.touk.nussknacker.ui.api.ScenarioTestingApiHttpService.TestingError._
import pl.touk.nussknacker.ui.api.ScenarioTestingApiHttpService.TestingError.BadRequestTestingError._
import pl.touk.nussknacker.ui.api.ScenarioTestingApiHttpService.TestingError.NotFoundTestingError._
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.ParametersValidationResultDto
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.{ResultsWithCountsDto, ScenarioTestData}
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
import pl.touk.nussknacker.ui.api.description.scenarioTesting.ScenarioTestingApiEndpoints
import pl.touk.nussknacker.ui.api.utils.ScenarioHttpServiceExtensions
import pl.touk.nussknacker.ui.api.utils.ValidationErrorOps.ValidationErrorOps
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.deployment.DeploymentManagerDispatcher
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.test.PreliminaryScenarioTestDataSerDe.SerializationError
import pl.touk.nussknacker.ui.process.test.ScenarioTestService
import pl.touk.nussknacker.ui.process.test.ScenarioTestService.GenerateTestDataError
import pl.touk.nussknacker.ui.security.api.{AuthManager, LoggedUser}
import pl.touk.nussknacker.ui.validation.ParametersValidator
import sttp.tapir.{Codec, CodecFormat}
import sttp.tapir.EndpointIO.Example

import scala.concurrent.{ExecutionContext, Future}

class ScenarioTestingApiHttpService(
    authManager: AuthManager,
    scenarioAuthorizer: AuthorizeProcess,
    processingTypeToParametersValidator: ProcessingTypeDataProvider[ParametersValidator, _],
    processingTypeToScenarioTestServices: ProcessingTypeDataProvider[ScenarioTestService, _],
    dmDispatcher: DeploymentManagerDispatcher,
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
            processId <- EitherT
              .fromOption[Future](scenarioWithDetails.processId, noScenarioError(scenarioName): TestingError)
            processIdWithName = ProcessIdWithName(processId, scenarioName)
            scenarioTestService = processingTypeToScenarioTestServices.forProcessingTypeUnsafe(
              scenarioWithDetails.processingType
            )
            capabilities = scenarioTestService.getTestingCapabilities(
              scenarioGraph,
              scenarioWithDetails.processVersionUnsafe,
            )
            liveDataPreviewCapability <- EitherT.right(
              dmDispatcher
                .deploymentManager(processIdWithName)
                .map {
                  case Some(deploymentManager) => deploymentManager.liveDataPreviewSupport
                  case None                    => NoLiveDataPreviewSupport
                }
                .map {
                  case _: LiveDataPreviewSupported =>
                    CapabilityStatus.available
                  case NoLiveDataPreviewSupport =>
                    CapabilityStatus.NotAvailable[EmptyDetails](NotAvailableReason.NotSupportedByScenarioType)
                }
            )
            result = capabilities match {
              case Left(TestingCapabilitiesError.NoSourcesError) =>
                def status[T <: TestCapabilityDetails] =
                  CapabilityStatus.NotAvailable[T](NotAvailableReason.NoSources)
                ScenarioTestCapabilities(status, status, liveDataPreviewCapability)
              case Left(TestingCapabilitiesError.SourceCompilationError) =>
                def status[T <: TestCapabilityDetails] =
                  CapabilityStatus.NotAvailable[T](NotAvailableReason.InvalidScenario)
                ScenarioTestCapabilities(status, status, liveDataPreviewCapability)
              case Right(capabilities) =>
                ScenarioTestCapabilities(
                  testWithParameters = if (capabilities.canTestWithForm) {
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
                  } else {
                    CapabilityStatus.NotAvailable(NotAvailableReason.NotSupportedBySources)
                  },
                  testWithGeneratedData =
                    if (capabilities.canBeTested && capabilities.canGenerateTestData) CapabilityStatus.available
                    else CapabilityStatus.NotAvailable(NotAvailableReason.NotSupportedBySources),
                  liveDataPreview = liveDataPreviewCapability
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
              case ScenarioTestData.WithGeneratedData(numberOfSamples) =>
                scenarioTestService.generateData(
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
            skipResultsPerTransition.getOrElse(SkipResultsPerTransition(false))
          )
        }
      }
  }

  expose {
    scenarioTestingApiEndpoints.scenarioLiveDataEndpoint
      .serverSecurityLogic(authorizeKnownUser[TestingError])
      .serverLogicEitherT { implicit loggedUser =>
        { case (scenarioName, skipResultsPerNode, skipResultsPerTransition) =>
          for {
            scenarioWithDetails <- getScenarioWithDetailsByName(scenarioName)
            processId <- EitherT
              .fromOption[Future](scenarioWithDetails.processId, noScenarioError(scenarioName): TestingError)
            processIdWithName = ProcessIdWithName(processId, scenarioName)
            _ <- isAuthorized(processId, Permission.Deploy)
            deploymentManager <- EitherT[Future, TestingError, DeploymentManager] {
              dmDispatcher.deploymentManager(processIdWithName).map {
                case Some(deploymentManager) => Right(deploymentManager)
                case None                    => Left(NoScenario(scenarioName))
              }
            }
            testResults <- EitherT[Future, TestingError, TestResults[Json]] {
              deploymentManager.liveDataPreviewSupport match {
                case supported: LiveDataPreviewSupported =>
                  supported.getLiveData(processIdWithName).map {
                    case Some(results) =>
                      Right(results)
                    case None =>
                      Left(TestingError.UnsupportedOperation("There are no live data available for this scenario"))
                  }
                case NoLiveDataPreviewSupport =>
                  Future.successful(
                    Left(TestingError.UnsupportedOperation("This scenario does not support live data preview"))
                  )
              }
            }
            scenarioTestService = processingTypeToScenarioTestServices.forProcessingTypeUnsafe(
              scenarioWithDetails.processingType
            )
            resultsWithCounts = scenarioTestService.resultsWithCounts(
              testResults,
              scenarioWithDetails.scenarioGraphUnsafe,
              scenarioWithDetails.processVersionUnsafe,
              scenarioWithDetails.isFragment
            )
          } yield ResultsWithCountsDto.from(
            resultsWithCounts,
            skipResultsPerNode.getOrElse(SkipResultsPerNode(false)),
            skipResultsPerTransition.getOrElse(SkipResultsPerTransition(false))
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
                      .map[TestingError](error => TestingError.UnsupportedOperation(error.message))
                  )
                  .map(validator.validate(sourceParameters, _)(metaData))
              case ScenarioTestData.WithGeneratedData(numberOfSamples) =>
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
              scenarioTestService.generateData(
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

  private def toDto(error: GenerateTestDataError): TestingError = {
    error match {
      case GenerateTestDataError.ScenarioTestDataGenerationError(cause) =>
        cause match {
          case ScenarioTestDataGenerationError.ScenarioGraphValidationError(nodesWithErrors) =>
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
          case ScenarioTestDataGenerationError.NoDataGenerated =>
            NoDataGenerated
          case ScenarioTestDataGenerationError.NoSourcesWithTestDataGeneration =>
            NoSourcesWithTestDataGeneration
        }
      case GenerateTestDataError.ScenarioTestDataSerializationError(cause) =>
        cause match {
          case SerializationError.TooManyCharactersGenerated(length, limit) =>
            TooManyCharactersGenerated(length, limit)
        }
      case GenerateTestDataError.TooManySamplesRequestedError(maxSamples) =>
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

object ScenarioTestingApiHttpService {

  sealed trait TestingError

  object TestingError {

    final case object NoPermission extends TestingError with CustomAuthorizationError

    sealed trait BadRequestTestingError extends TestingError

    object BadRequestTestingError {
      final case class TooManyCharactersGenerated(length: Int, limit: Int)    extends BadRequestTestingError
      final case class TooManySamplesRequested(maxSamples: Int)               extends BadRequestTestingError
      final case class ScenarioGraphValidationError(errors: ValidationErrors) extends BadRequestTestingError

      implicit val badRequestTestingErrorCodec: Codec[String, BadRequestTestingError, CodecFormat.TextPlain] = {
        BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[BadRequestTestingError] {
          case ScenarioGraphValidationError(errors) =>
            errors.toHumanReadableMessage
          case TooManyCharactersGenerated(length, limit) =>
            TestingApiErrorMessages.generatedTestData.tooManyCharacters(length, limit)
          case TooManySamplesRequested(maxSamples) =>
            TestingApiErrorMessages.generatedTestData.requestedTooManySamplesToGenerate(maxSamples)
        }
      }

    }

    sealed trait NotFoundTestingError extends TestingError

    object NotFoundTestingError {
      final case class NoScenario(scenarioName: ProcessName) extends NotFoundTestingError
      final case object NoDataGenerated                      extends NotFoundTestingError
      final case object NoSourcesWithTestDataGeneration      extends NotFoundTestingError

      implicit val notFoundTestingErrorCodec: Codec[String, NotFoundTestingError, CodecFormat.TextPlain] = {
        BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NotFoundTestingError] {
          case NoScenario(scenarioName) => s"No scenario ${scenarioName.value} found"
          case NoDataGenerated          => TestingApiErrorMessages.generatedTestData.couldNotProvideTestDataSample
          case NoSourcesWithTestDataGeneration =>
            TestingApiErrorMessages.generatedTestData.noSourcesWithTestDataGeneration
        }
      }

      implicit val noScenarioCodec: Codec[String, NoScenario, CodecFormat.TextPlain] = {
        BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NoScenario](e =>
          s"No scenario ${e.scenarioName} found"
        )
      }

    }

    final case class UnsupportedOperation(message: String) extends TestingError

    implicit val UnsupportedOperationCodec: Codec[String, UnsupportedOperation, CodecFormat.TextPlain] = {
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[UnsupportedOperation](_.message)
    }

    final case class ErrorResult(message: String) extends TestingError

    implicit val ErrorResultCodec: Codec[String, ErrorResult, CodecFormat.TextPlain] = {
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[ErrorResult](_.message)
    }

  }

  object Examples {

    val noScenarioExample: Example[NoScenario] = Example.of(
      summary = Some("No scenario {scenarioName} found"),
      value = NoScenario(ProcessName("'example scenario'"))
    )

  }

}
