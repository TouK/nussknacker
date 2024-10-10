package pl.touk.nussknacker.ui.api

import cats.data.EitherT
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.process.{ProcessName, ProcessingType}
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.ui.api.BaseHttpService.CustomAuthorizationError
import pl.touk.nussknacker.ui.api.TestingApiHttpService.TestingError
import pl.touk.nussknacker.ui.api.TestingApiHttpService.TestingError._
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.ParametersValidationResultDto
import pl.touk.nussknacker.ui.api.description.TestingApiEndpoints
import pl.touk.nussknacker.ui.api.utils.ScenarioHttpServiceExtensions
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.test.ScenarioTestService
import pl.touk.nussknacker.ui.security.api.AuthManager
import pl.touk.nussknacker.ui.validation.ParametersValidator
import sttp.model.StatusCode.{BadRequest, NotFound}
import sttp.tapir.EndpointIO.Example
import sttp.tapir.{Codec, CodecFormat, EndpointOutput, oneOfVariantFromMatchType, plainBody}

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
            inputParameters = scenarioTestService.testParametersDefinition(
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
              scenarioWithDetails.isFragment,
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
              scenarioWithDetails.isFragment
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
                  Future(Left(TestDataGenerationError(error)))
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

    final case class TestDataGenerationError(msg: String)             extends TestingError
    final case class NoScenario(scenarioName: ProcessName)            extends TestingError
    final case class NoProcessingType(processingType: ProcessingType) extends TestingError
    final case object NoPermission                                    extends TestingError with CustomAuthorizationError
    final case class MalformedTypingResult(msg: String)               extends TestingError

    implicit val noScenarioCodec: Codec[String, NoScenario, CodecFormat.TextPlain] = {
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NoScenario](e => s"No scenario ${e.scenarioName} found")
    }

    implicit val noProcessingTypeCodec: Codec[String, NoProcessingType, CodecFormat.TextPlain] = {
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NoProcessingType](e =>
        s"ProcessingType type: ${e.processingType} not found"
      )
    }

    implicit val malformedTypingResultCoded: Codec[String, MalformedTypingResult, CodecFormat.TextPlain] = {
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[MalformedTypingResult](e =>
        s"The request content was malformed:\n${e.msg}"
      )
    }

    implicit val testDataGenerationErrortCoded: Codec[String, TestDataGenerationError, CodecFormat.TextPlain] = {
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[TestDataGenerationError](e =>
        s"Error during generation of test data: \n${e.msg}"
      )
    }

  }

  object Examples {

    val noScenarioExample: EndpointOutput.OneOfVariant[NoScenario] =
      oneOfVariantFromMatchType(
        NotFound,
        plainBody[NoScenario]
          .example(
            Example.of(
              summary = Some("No scenario {scenarioName} found"),
              value = NoScenario(ProcessName("'example scenario'"))
            )
          )
      )

    val testDataGenerationErrorExample: EndpointOutput.OneOfVariant[TestDataGenerationError] =
      oneOfVariantFromMatchType(
        BadRequest,
        plainBody[TestDataGenerationError]
          .example(
            Example.of(
              summary = Some("There was a problem with parsing test data"),
              value = TestDataGenerationError("Parsing of data failed")
            )
          )
      )

    val malformedTypingResultExample: EndpointOutput.OneOfVariant[MalformedTypingResult] =
      oneOfVariantFromMatchType(
        BadRequest,
        plainBody[MalformedTypingResult]
          .example(
            Example.of(
              summary = Some("Malformed TypingResult sent in request"),
              value = MalformedTypingResult(
                "Couldn't decode value 'WrongType'. Allowed values: 'TypedUnion,TypedDict,TypedObjectTypingResult,TypedTaggedValue,TypedClass,TypedObjectWithValue,TypedNull,Unknown"
              )
            )
          )
      )

    val noProcessingTypeExample: EndpointOutput.OneOfVariant[NoProcessingType] =
      oneOfVariantFromMatchType(
        NotFound,
        plainBody[NoProcessingType]
          .example(
            Example.of(
              summary = Some("ProcessingType type: {processingType} not found"),
              value = NoProcessingType("'processingType'")
            )
          )
      )

  }

}
