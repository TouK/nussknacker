package pl.touk.nussknacker.ui.api

import cats.data.EitherT
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.definition.test.TestInfoProvider.ScenarioTestDataGenerationError
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.ui.api.BaseHttpService.CustomAuthorizationError
import pl.touk.nussknacker.ui.api.TestingApiHttpService.TestingError
import pl.touk.nussknacker.ui.api.TestingApiHttpService.TestingError._
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.ParametersValidationResultDto
import pl.touk.nussknacker.ui.api.description.TestingApiEndpoints
import pl.touk.nussknacker.ui.api.utils.ScenarioHttpServiceExtensions
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.test.PreliminaryScenarioTestDataSerDe.SerializationError
import pl.touk.nussknacker.ui.process.test.ScenarioTestService
import pl.touk.nussknacker.ui.process.test.ScenarioTestService.GenerateTestDataError
import pl.touk.nussknacker.ui.security.api.AuthManager
import pl.touk.nussknacker.ui.validation.ParametersValidator
import sttp.model.StatusCode.{BadRequest, NotFound}
import sttp.tapir.{Codec, CodecFormat, EndpointOutput, oneOfVariant, oneOfVariantFromMatchType, plainBody}

import scala.concurrent.{ExecutionContext, Future}

class TestingApiHttpService(
    authManager: AuthManager,
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

  private val testingApiEndpoints = new TestingApiEndpoints(authManager.authenticationEndpointInput())

  expose {
    testingApiEndpoints.scenarioTestingAdhocValidateEndpoint
      .serverSecurityLogic(authorizeKnownUser[TestingError])
      .serverLogicEitherT { implicit loggedUser =>
        { case (scenarioName, request) =>
          for {
            scenarioWithDetails <- getScenarioWithDetailsByName(scenarioName)
            validator = processingTypeToParametersValidator.forProcessingTypeUnsafe(scenarioWithDetails.processingType)
            scenarioTestService = processingTypeToScenarioTestServices.forProcessingTypeUnsafe(
              scenarioWithDetails.processingType
            )
            inputParameters = scenarioTestService.validateAndGetTestParametersDefinition(
              request.scenarioGraph,
              scenarioWithDetails.processVersionUnsafe,
              scenarioWithDetails.isFragment
            )
            metaData          = request.scenarioGraph.properties.toMetaData(scenarioName)
            validationResults = validator.validate(request.sourceParameters, inputParameters)(metaData)
          } yield ParametersValidationResultDto(validationResults, validationPerformed = true)
        }
      }
  }

  expose {
    testingApiEndpoints.scenarioTestingCapabilitiesEndpoint
      .serverSecurityLogic(authorizeKnownUser[TestingError])
      .serverLogicEitherT { implicit loggedUser =>
        { case (scenarioName, scenarioGraph) =>
          for {
            scenarioWithDetails <- getScenarioWithDetailsByName(scenarioName)
            scenarioTestService = processingTypeToScenarioTestServices.forProcessingTypeUnsafe(
              scenarioWithDetails.processingType
            )
            capabilities = scenarioTestService.getTestingCapabilities(
              scenarioGraph,
              scenarioWithDetails.processVersionUnsafe,
            )
          } yield capabilities
        }
      }
  }

  expose {
    testingApiEndpoints.scenarioTestingParametersEndpoint
      .serverSecurityLogic(authorizeKnownUser[TestingError])
      .serverLogicEitherT { implicit loggedUser =>
        { case (scenarioName, scenarioGraph) =>
          for {
            scenarioWithDetails <- getScenarioWithDetailsByName(scenarioName)
            scenarioTestService = processingTypeToScenarioTestServices.forProcessingTypeUnsafe(
              scenarioWithDetails.processingType
            )
            parametersDefinition = scenarioTestService.testUISourceParametersDefinition(
              scenarioGraph,
              scenarioWithDetails.processVersionUnsafe,
            )
          } yield parametersDefinition
        }
      }
  }

  expose {
    testingApiEndpoints.scenarioTestingGenerateEndpoint
      .serverSecurityLogic(authorizeKnownUser[TestingError])
      .serverLogicEitherT { implicit loggedUser =>
        { case (scenarioName, testSampleSize, scenarioGraph) =>
          for {
            scenarioWithDetails <- getScenarioWithDetailsByName(scenarioName)
            scenarioTestService = processingTypeToScenarioTestServices.forProcessingTypeUnsafe(
              scenarioWithDetails.processingType
            )
            parametersDefinition <- EitherT[Future, TestingError, String](
              scenarioTestService.generateData(
                scenarioGraph,
                scenarioWithDetails.processVersionUnsafe,
                scenarioWithDetails.isFragment,
                testSampleSize
              ) match {
                case Left(error) =>
                  logger.error(s"Error during generation of test data: $error")
                  Future(
                    Left(
                      error match {
                        case GenerateTestDataError.ScenarioTestDataGenerationError(cause) =>
                          cause match {
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
                    )
                  )
                case Right(rawScenarioTestData) =>
                  Future(Right(rawScenarioTestData.content))
              }
            )
          } yield parametersDefinition
        }
      }
  }

}

object TestingApiHttpService {

  sealed trait TestingError

  object TestingError {

    final case class NoScenario(scenarioName: ProcessName) extends TestingError
    final case object NoPermission                         extends TestingError with CustomAuthorizationError
    final case object NoDataGenerated                      extends TestingError
    final case object NoSourcesWithTestDataGeneration      extends TestingError
    final case class TooManyCharactersGenerated(length: Int, limit: Int) extends TestingError
    final case class TooManySamplesRequested(maxSamples: Int)            extends TestingError

    implicit val noScenarioCodec: Codec[String, NoScenario, CodecFormat.TextPlain] = {
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NoScenario](e => s"No scenario ${e.scenarioName} found")
    }

    implicit val noDataGeneratedCodec: Codec[String, NoDataGenerated.type, CodecFormat.TextPlain] = {
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NoDataGenerated.type](_ =>
        TestingApiErrorMessages.noDataGenerated
      )
    }

    implicit val noSourcesWithTestDataGenerationCodec
        : Codec[String, NoSourcesWithTestDataGeneration.type, CodecFormat.TextPlain] = {
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NoSourcesWithTestDataGeneration.type](_ =>
        TestingApiErrorMessages.noSourcesWithTestDataGeneration
      )
    }

    implicit val tooManyCharactersGeneratedCodec: Codec[String, TooManyCharactersGenerated, CodecFormat.TextPlain] = {
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[TooManyCharactersGenerated] {
        case TooManyCharactersGenerated(length, limit) =>
          TestingApiErrorMessages.tooManyCharactersGenerated(length, limit)
      }
    }

    implicit val tooManySamplesRequestedCodec: Codec[String, TooManySamplesRequested, CodecFormat.TextPlain] = {
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[TooManySamplesRequested] {
        case TooManySamplesRequested(maxSamples) =>
          TestingApiErrorMessages.requestedTooManySamplesToGenerate(maxSamples)
      }
    }

  }

  object ErrorOutputs {

    val noScenarioErrorOutput: EndpointOutput.OneOfVariant[NoScenario] =
      oneOfVariantFromMatchType(
        NotFound,
        plainBody[NoScenario]
      )

    val noDataGeneratedErrorOutput: EndpointOutput.OneOfVariant[NoDataGenerated.type] =
      oneOfVariant(
        NotFound,
        plainBody[NoDataGenerated.type]
      )

    val noSourcesWithTestDataGenerationErrorOutput: EndpointOutput.OneOfVariant[NoSourcesWithTestDataGeneration.type] =
      oneOfVariant(
        NotFound,
        plainBody[NoSourcesWithTestDataGeneration.type]
      )

    val tooManyCharactersGeneratedErrorOutput: EndpointOutput.OneOfVariant[TooManyCharactersGenerated] =
      oneOfVariantFromMatchType(
        BadRequest,
        plainBody[TooManyCharactersGenerated]
      )

    val tooManySamplesRequestedErrorOutput: EndpointOutput.OneOfVariant[TooManySamplesRequested] =
      oneOfVariantFromMatchType(
        BadRequest,
        plainBody[TooManySamplesRequested]
      )

  }

}
