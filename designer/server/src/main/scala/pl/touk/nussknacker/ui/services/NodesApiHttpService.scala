package pl.touk.nussknacker.ui.services

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.graph.{ProcessProperties, ScenarioGraph}
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName, ProcessingType}
import pl.touk.nussknacker.engine.spel.ExpressionSuggestion
import pl.touk.nussknacker.ui.additionalInfo.AdditionalInfoProviders
import pl.touk.nussknacker.ui.api.NodesApiEndpoints.Dtos.{ExpressionSuggestionDto, NodeValidationRequest, NodeValidationRequestDto, NodeValidationResult, NodeValidationResultDto, NodesError, ParametersValidationRequest, ParametersValidationRequestDto, ParametersValidationResultDto, mapVariableTypesOrThrowError, prepareTypingResultDecoder}
import pl.touk.nussknacker.ui.api.NodesApiEndpoints
import pl.touk.nussknacker.ui.api.NodesApiEndpoints.Dtos
import pl.touk.nussknacker.ui.api.NodesApiEndpoints.Dtos.NodesError.{MalformedTypingResult, NoProcessingType, NoScenario}
import pl.touk.nussknacker.ui.process.ProcessService.GetScenarioWithDetailsOptions
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, LoggedUser}
import pl.touk.nussknacker.ui.suggester.ExpressionSuggester
import pl.touk.nussknacker.ui.util.EitherTImplicits
import pl.touk.nussknacker.ui.validation.{NodeValidator, ParametersValidator, UIProcessValidator}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class NodesApiHttpService(
    config: Config,
    authenticator: AuthenticationResources,
    typeToConfig: ProcessingTypeDataProvider[ModelData, _],
    typeToProcessValidator: ProcessingTypeDataProvider[UIProcessValidator, _],
    typeToNodeValidator: ProcessingTypeDataProvider[NodeValidator, _],
    typeToExpressionSuggester: ProcessingTypeDataProvider[ExpressionSuggester, _],
    typeToParametersValidator: ProcessingTypeDataProvider[ParametersValidator, _],
    protected val scenarioService: ProcessService
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(config, authenticator)
    with LazyLogging {
  import EitherTImplicits._

  private val nodesApiEndpoints = new NodesApiEndpoints(authenticator.authenticationMethod())

  private val additionalInfoProviders = new AdditionalInfoProviders(typeToConfig)

  expose {
    nodesApiEndpoints.nodesAdditionalInfoEndpoint
      .serverSecurityLogic(authorizeKnownUser[NodesError])
      .serverLogicEitherT { implicit loggedUser =>
        { case (scenarioName, nodeData) =>
          for {
            scenarioId <- getScenarioIdByName(scenarioName)
            scenario <- scenarioService
              .getLatestProcessWithDetails(
                ProcessIdWithName(scenarioId, scenarioName),
                GetScenarioWithDetailsOptions.detailsOnly
              )
              .eitherT()
            additionalInfo <- additionalInfoProviders
              .prepareAdditionalInfoForNode(nodeData, scenario.processingType)
              .eitherT()
          } yield additionalInfo
        }
      }
  }

  expose {
    nodesApiEndpoints.nodesValidationEndpoint
      .serverSecurityLogic(authorizeKnownUser[NodesError])
      .serverLogicEitherT { implicit loggedUser =>
        { case (scenarioName, nodeValidationRequestDto) =>
          for {
            scenarioId <- getScenarioIdByName(scenarioName)
            scenario <- scenarioService
              .getLatestProcessWithDetails(
                ProcessIdWithName(scenarioId, scenarioName),
                GetScenarioWithDetailsOptions.detailsOnly
              )
              .eitherT()
            modelData <- getModelData(scenario.processingType)
            nodeValidator = typeToNodeValidator.forTypeUnsafe(scenario.processingType)
            nodeData   <- dtoToRequest(nodeValidationRequestDto, modelData)
            validation <- getValidation(nodeValidator, scenarioName, nodeData)
            validationDto = NodeValidationResultDto.apply(validation)
          } yield validationDto
        }
      }
  }

  expose {
    nodesApiEndpoints.propertiesAdditionalInfoEndpoint
      .serverSecurityLogic(authorizeKnownUser[NodesError])
      .serverLogicEitherT { implicit loggedUser =>
        { case (scenarioName, scenarioProperties) =>
          for {
            scenarioId <- getScenarioIdByName(scenarioName)
            scenario <- scenarioService
              .getLatestProcessWithDetails(
                ProcessIdWithName(scenarioId, scenarioName),
                GetScenarioWithDetailsOptions.detailsOnly
              )
              .eitherT()
            additionalInfo <- additionalInfoProviders
              .prepareAdditionalInfoForProperties(
                scenarioProperties.toMetaData(scenarioName),
                scenario.processingType
              )
              .eitherT()
          } yield additionalInfo
        }
      }
  }

  expose {
    nodesApiEndpoints.propertiesValidationEndpoint
      .serverSecurityLogic(authorizeKnownUser[NodesError])
      .serverLogicEitherT { implicit loggedUser =>
        { case (scenarioName, request) =>
          for {
            scenarioId <- getScenarioIdByName(scenarioName)
            scenarioWithDetails <- scenarioService
              .getLatestProcessWithDetails(
                ProcessIdWithName(scenarioId, scenarioName),
                GetScenarioWithDetailsOptions.detailsOnly
              )
              .eitherT()
            scenario = ScenarioGraph(ProcessProperties(request.additionalFields), Nil, Nil)
            result = typeToProcessValidator
              .forTypeUnsafe(scenarioWithDetails.processingType)
              .validate(scenario, request.name, scenarioWithDetails.isFragment)
            validation = NodeValidationResultDto(
              parameters = None,
              expressionType = None,
              validationErrors = result.errors.processPropertiesErrors,
              validationPerformed = true
            )
          } yield validation
        }
      }
  }

  expose {
    nodesApiEndpoints.parametersValidationEndpoint
      .serverSecurityLogic(authorizeKnownUser[NodesError])
      .serverLogicEitherT { implicit loggedUser =>
        { case (processingType, request) =>
          for {
            modelData <- getModelData(processingType)
            validator = typeToParametersValidator.forTypeUnsafe(processingType)
            requestWithTypingResult <- parametersValidationRequestFromDto(request, modelData)
            validationResults = validator.validate(requestWithTypingResult)
          } yield ParametersValidationResultDto(validationResults, validationPerformed = true)
        }
      }
  }

  expose {
    nodesApiEndpoints.parametersSuggestionsEndpoint
      .serverSecurityLogic(authorizeKnownUser[NodesError])
      .serverLogicEitherT { implicit loggedUser =>
        { case (processingType, request) =>
          for {
            modelData <- getModelData(processingType)
            expressionSuggester = typeToExpressionSuggester.forTypeUnsafe(processingType)
            suggestions   <- getSuggestions(expressionSuggester, request, modelData)
            suggestionDto <- toResponse(suggestions)
          } yield suggestionDto
        }
      }
  }

  private def getScenarioIdByName(scenarioName: ProcessName) = {
    scenarioService
      .getProcessId(scenarioName)
      .toRightEitherT(NoScenario(scenarioName))
  }

  private def dtoToRequest(nodeValidationRequestDto: NodeValidationRequestDto, modelData: ModelData) = {
    Future[Either[NodesError, NodeValidationRequest]](
      try {
        Right(NodeValidationRequest.apply(nodeValidationRequestDto)(prepareTypingResultDecoder(modelData)))
      } catch {
        case e: Exception =>
          Left(MalformedTypingResult(e.getMessage))
      }
    )
      .eitherT()
  }

  private def getModelData(processingType: ProcessingType)(implicit user: LoggedUser) = {
    Future[Either[NodesError, ModelData]](
      try {
        Right(typeToConfig.forTypeUnsafe(processingType))
      } catch {
        case _: IllegalArgumentException =>
          Left(NoProcessingType(processingType))
      }
    )
      .eitherT()
  }

  private def getValidation(nodeValidator: NodeValidator, scenarioName: ProcessName, nodeData: NodeValidationRequest)(
      implicit user: LoggedUser
  ) =
    Future[Either[NodesError, NodeValidationResult]](
      try {
        Right(nodeValidator.validate(scenarioName, nodeData))
      } catch {
        case e: ProcessNotFoundError =>
          Left(NoScenario(ProcessName(e.name.value)))
      }
    )
      .eitherT()

  private def parametersValidationRequestFromDto(request: ParametersValidationRequestDto, modelData: ModelData) = {
    Future[Either[NodesError, ParametersValidationRequest]](
      try {
        Right(ParametersValidationRequest.apply(request)(prepareTypingResultDecoder(modelData)))
      } catch {
        case e: Exception =>
          Left(MalformedTypingResult(e.getMessage))
      }
    )
      .eitherT()
  }

  private def toResponse(suggestions: Future[List[ExpressionSuggestion]]) = {
    Future[Either[NodesError, List[ExpressionSuggestionDto]]](
      suggestions.value match {
        case Some(value) =>
          value match {
            case Failure(exception) => Left(MalformedTypingResult(exception.getMessage))
            case Success(list)      => Right(list.map { expression => ExpressionSuggestionDto(expression) })
          }
        case None => // Not completed yet
          Left(NoScenario(ProcessName("idk")))
      }
    )
      .eitherT()
  }

  private def getSuggestions(
      expressionSuggester: ExpressionSuggester,
      request: Dtos.ExpressionSuggestionRequestDto,
      modelData: ModelData
  ) = {
    Future[Either[NodesError, Future[List[ExpressionSuggestion]]]](
      try {
        Right(
          expressionSuggester.expressionSuggestions(
            request.expression,
            request.caretPosition2d,
            mapVariableTypesOrThrowError(request.variableTypes, prepareTypingResultDecoder(modelData))
          )
        )
      } catch {
        case e: Exception => Left(MalformedTypingResult(e.getMessage))
      }
    )
      .eitherT()
  }

}
