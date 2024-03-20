package pl.touk.nussknacker.ui.services

import cats.data.EitherT
import com.typesafe.scalalogging.LazyLogging
import io.circe.Decoder
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.graph.{ProcessProperties, ScenarioGraph}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessName, ProcessingType}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.spel.ExpressionSuggestion
import pl.touk.nussknacker.restmodel.definition.UIValueParameter
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.ui.UnauthorizedError
import pl.touk.nussknacker.ui.additionalInfo.AdditionalInfoProviders
import pl.touk.nussknacker.ui.api.NodesApiEndpoints
import pl.touk.nussknacker.ui.api.NodesApiEndpoints.Dtos
import pl.touk.nussknacker.ui.api.NodesApiEndpoints.Dtos.NodesError.{
  MalformedTypingResult,
  NoPermission,
  NoProcessingType,
  NoScenario
}
import pl.touk.nussknacker.ui.api.NodesApiEndpoints.Dtos.{
  ExpressionSuggestionDto,
  NodeValidationRequest,
  NodeValidationRequestDto,
  NodeValidationResult,
  NodeValidationResultDto,
  NodesError,
  ParametersValidationRequest,
  ParametersValidationRequestDto,
  ParametersValidationResultDto,
  decodeVariableTypes,
  prepareTypingResultDecoder
}
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.ProcessService.GetScenarioWithDetailsOptions
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, LoggedUser}
import pl.touk.nussknacker.ui.suggester.ExpressionSuggester
import pl.touk.nussknacker.ui.util.EitherTImplicits
import pl.touk.nussknacker.ui.validation.{NodeValidator, ParametersValidator, UIProcessValidator}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class NodesApiHttpService(
    authenticator: AuthenticationResources,
    typeToConfig: ProcessingTypeDataProvider[ModelData, _],
    typeToProcessValidator: ProcessingTypeDataProvider[UIProcessValidator, _],
    typeToNodeValidator: ProcessingTypeDataProvider[NodeValidator, _],
    typeToExpressionSuggester: ProcessingTypeDataProvider[ExpressionSuggester, _],
    typeToParametersValidator: ProcessingTypeDataProvider[ParametersValidator, _],
    protected val scenarioService: ProcessService
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(authenticator)
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
            scenario   <- getScenarioWithDetails(scenarioId, scenarioName)
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
            scenario   <- getScenarioWithDetails(scenarioId, scenarioName)
            modelData  <- getModelData(scenario.processingType)
            nodeValidator = typeToNodeValidator.forTypeUnsafe(scenario.processingType)
            nodeData   <- dtoToNodeRequest(nodeValidationRequestDto, modelData)
            validation <- getNodeValidation(nodeValidator, scenarioName, nodeData)
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
            scenario   <- getScenarioWithDetails(scenarioId, scenarioName)
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
            scenarioId          <- getScenarioIdByName(scenarioName)
            scenarioWithDetails <- getScenarioWithDetails(scenarioId, scenarioName)
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
            requestWithTypingResult <- dtoToParameterRequest(request, modelData)
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
            suggestionDto <- getExpressionSuggestion(suggestions)
          } yield suggestionDto
        }
      }
  }

  private def getScenarioIdByName(scenarioName: ProcessName) = {
    scenarioService
      .getProcessId(scenarioName)
      .toRightEitherT(NoScenario(scenarioName))
  }

  private def getScenarioWithDetails(scenarioId: ProcessId, scenarioName: ProcessName)(
      implicit user: LoggedUser
  ): EitherT[Future, NodesError, ScenarioWithDetails] = {
    scenarioWithDetails(scenarioId, scenarioName)
      .eitherT()
      .leftMap { no: NoScenario =>
        NodesError.NoScenario(no.scenarioName)
      }
  }

  private def scenarioWithDetails(scenarioId: ProcessId, scenarioName: ProcessName)(implicit user: LoggedUser) = {
    scenarioService
      .getLatestProcessWithDetails(
        ProcessIdWithName(scenarioId, scenarioName),
        GetScenarioWithDetailsOptions.detailsOnly
      )
      .map(scenario => Right(scenario))
      .recover { case _: Throwable => Left(NoScenario(scenarioName)) }
  }

  private def dtoToNodeRequest(nodeValidationRequestDto: NodeValidationRequestDto, modelData: ModelData) = {
    Future[Either[NodesError, NodeValidationRequest]](
      fromNodeRequestDto(nodeValidationRequestDto)(prepareTypingResultDecoder(modelData))
    ).eitherT()
  }

  private def getModelData(processingType: ProcessingType)(implicit user: LoggedUser) = {
    Future(
      Try(typeToConfig.forTypeUnsafe(processingType)).toEither.left.map {
        case _: IllegalArgumentException =>
          NoProcessingType(processingType)
        case _: UnauthorizedError =>
          NoPermission
      }
    )
      .eitherT()
  }

  private def getNodeValidation(
      nodeValidator: NodeValidator,
      scenarioName: ProcessName,
      nodeData: NodeValidationRequest
  )(
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

  private def dtoToParameterRequest(request: ParametersValidationRequestDto, modelData: ModelData) =
    Future[Either[NodesError, ParametersValidationRequest]](
      parametersValidationRequestFromDto(request, modelData)
    ).eitherT()

  private def getExpressionSuggestion(
      suggestions: Future[List[ExpressionSuggestion]]
  ): EitherT[Future, NodesError, List[ExpressionSuggestionDto]] = {
    toExpressionSuggestionResponse(suggestions).leftMap(identity)
  }

  private def toExpressionSuggestionResponse(suggestions: Future[List[ExpressionSuggestion]]) = {
    EitherT
      .liftF(
        suggestions
          .map(expressionList =>
            expressionList
              .map(expression => ExpressionSuggestionDto(expression))
          )
      )
      // This should not happen as getSuggestions should have already deal with Malformed requests
      .leftMap { _: Any => MalformedTypingResult("Internally passed malformed TypingResult") }
  }

  private def getSuggestions(
      expressionSuggester: ExpressionSuggester,
      request: Dtos.ExpressionSuggestionRequestDto,
      modelData: ModelData
  ) = {
    Future[Either[NodesError, Future[List[ExpressionSuggestion]]]](
      decodeVariableTypes(request.variableTypes, prepareTypingResultDecoder(modelData)) match {
        case Left(value) => Left(value)
        case Right(localVariables) =>
          Right(
            expressionSuggester.expressionSuggestions(
              request.expression,
              request.caretPosition2d,
              localVariables
            )
          )
      }
    )
      .eitherT()
  }

  private def fromNodeRequestDto(
      node: NodeValidationRequestDto
  )(typingResultDecoder: Decoder[TypingResult]): Either[NodesError, NodeValidationRequest] = {
    val variableTypes = decodeVariableTypes(node.variableTypes, typingResultDecoder) match {
      case Left(value)  => return Left(value)
      case Right(value) => value
    }
    val branchVariableTypes = node.branchVariableTypes.map { outerMap =>
      outerMap.map { case (name, innerMap) =>
        decodeVariableTypes(innerMap, typingResultDecoder) match {
          case Right(changedMap) => (name, changedMap)
          case Left(value)       => return Left(value)
        }
      }
    }
    Right(
      NodeValidationRequest(
        nodeData = node.nodeData,
        processProperties = node.processProperties,
        variableTypes = variableTypes,
        branchVariableTypes = branchVariableTypes,
        outgoingEdges = node.outgoingEdges
      )
    )
  }

  private def parametersValidationRequestFromDto(
      request: ParametersValidationRequestDto,
      modelData: ModelData
  ): Either[NodesError, ParametersValidationRequest] = {
    val typingResultDecoder = prepareTypingResultDecoder(modelData)
    val parameters = request.parameters.map { parameter =>
      UIValueParameter(
        name = parameter.name,
        typ = typingResultDecoder
          .decodeJson(parameter.typ) match {
          case Left(failure)       => return Left(MalformedTypingResult(failure.getMessage()))
          case Right(typingResult) => typingResult
        },
        expression = parameter.expression
      )
    }
    val variableTypes =
      decodeVariableTypes(request.variableTypes, typingResultDecoder) match {
        case Left(value)  => return Left(value)
        case Right(value) => value
      }
    Right(ParametersValidationRequest(parameters, variableTypes))
  }

}
