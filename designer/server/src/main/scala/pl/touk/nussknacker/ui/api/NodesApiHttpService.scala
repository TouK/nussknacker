package pl.touk.nussknacker.ui.api

import cats.data.EitherT
import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.LazyLogging
import io.circe.Decoder
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.graph.{ProcessProperties, ScenarioGraph}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessName, ProcessingType}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.spel.ExpressionSuggestion
import pl.touk.nussknacker.restmodel.definition.UIValueParameter
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.ui.additionalInfo.AdditionalInfoProviders
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.NodesError.{
  MalformedTypingResult,
  NoPermission,
  NoProcessingType,
  NoScenario
}
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.{
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
import pl.touk.nussknacker.ui.validation.{NodeValidator, ParametersValidator, UIProcessValidator}

import scala.concurrent.{ExecutionContext, Future}

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
            additionalInfo <- EitherT.right(
              additionalInfoProviders.prepareAdditionalInfoForNode(nodeData, scenario.processingType)
            )
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
            additionalInfo <- EitherT.right(
              additionalInfoProviders
                .prepareAdditionalInfoForProperties(
                  scenarioProperties.toMetaData(scenarioName),
                  scenario.processingType
                )
            )
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
            suggestions <- getSuggestions(expressionSuggester, request, modelData)
            suggestionDto = suggestions.map(expression => ExpressionSuggestionDto(expression))
          } yield suggestionDto
        }
      }
  }

  private def getScenarioIdByName(scenarioName: ProcessName): EitherT[Future, NoScenario, ProcessId] = {
    EitherT.fromOptionF(
      scenarioService.getProcessId(scenarioName),
      NoScenario(scenarioName)
    )
  }

  private def getScenarioWithDetails(scenarioId: ProcessId, scenarioName: ProcessName)(
      implicit user: LoggedUser
  ): EitherT[Future, NodesError, ScenarioWithDetails] = {
    EitherT(
      scenarioService
        .getLatestProcessWithDetails(
          ProcessIdWithName(scenarioId, scenarioName),
          GetScenarioWithDetailsOptions.detailsOnly
        )
        .map(scenario => Right(scenario))
        .recover { case ProcessNotFoundError(_) => Left(NoScenario(scenarioName)) }
    )
  }

  private def dtoToNodeRequest(
      nodeValidationRequestDto: NodeValidationRequestDto,
      modelData: ModelData
  ): EitherT[Future, NodesError, NodeValidationRequest] = {
    EitherT.fromEither(
      fromNodeRequestDto(nodeValidationRequestDto)(prepareTypingResultDecoder(modelData.modelClassLoader.classLoader))
    )
  }

  private def getModelData(
      processingType: ProcessingType
  )(implicit user: LoggedUser): EitherT[Future, NodesError, ModelData] = {
    EitherT.fromEither(
      typeToConfig
        .forTypeE(processingType)
        .left
        .map(_ => NoPermission)
        .flatMap(_.toRight(NoProcessingType(processingType)))
    )
  }

  private def getNodeValidation(
      nodeValidator: NodeValidator,
      scenarioName: ProcessName,
      nodeData: NodeValidationRequest
  )(
      implicit user: LoggedUser
  ): EitherT[Future, NodesError, NodeValidationResult] =
    EitherT.fromEither(
      try {
        Right(nodeValidator.validate(scenarioName, nodeData))
      } catch {
        case e: ProcessNotFoundError =>
          Left(NoScenario(ProcessName(e.name.value)))
      }
    )

  private def dtoToParameterRequest(
      request: ParametersValidationRequestDto,
      modelData: ModelData
  ): EitherT[Future, NodesError, ParametersValidationRequest] =
    EitherT.fromEither(parametersValidationRequestFromDto(request, modelData))

  private def getSuggestions(
      expressionSuggester: ExpressionSuggester,
      request: Dtos.ExpressionSuggestionRequestDto,
      modelData: ModelData
  ): EitherT[Future, NodesError, List[ExpressionSuggestion]] =
    for {
      localVariables <- EitherT.fromEither[Future](
        decodeVariableTypes(
          request.variableTypes,
          prepareTypingResultDecoder(modelData.modelClassLoader.classLoader)
        )
      )
      suggestions <- EitherT.right(
        expressionSuggester.expressionSuggestions(
          request.expression,
          request.caretPosition2d,
          localVariables
        )
      )
    } yield suggestions

  private def fromNodeRequestDto(
      node: NodeValidationRequestDto
  )(typingResultDecoder: Decoder[TypingResult]): Either[NodesError, NodeValidationRequest] = {
    for {
      variableTypes <- decodeVariableTypes(node.variableTypes, typingResultDecoder)
      branchVariableTypes <- node.branchVariableTypes.map { outerMap =>
        outerMap.toList
          .map { case (name, innerMap) =>
            decodeVariableTypes(innerMap, typingResultDecoder).map((name, _))
          }
          .sequence
          .map(_.toMap)
      }.sequence
    } yield NodeValidationRequest(
      nodeData = node.nodeData,
      processProperties = node.processProperties,
      variableTypes = variableTypes,
      branchVariableTypes = branchVariableTypes,
      outgoingEdges = node.outgoingEdges
    )
  }

  private def parametersValidationRequestFromDto(
      request: ParametersValidationRequestDto,
      modelData: ModelData
  ): Either[NodesError, ParametersValidationRequest] = {
    val typingResultDecoder = prepareTypingResultDecoder(modelData.modelClassLoader.classLoader)
    for {
      parameters <- request.parameters.map { parameter =>
        typingResultDecoder
          .decodeJson(parameter.typ)
          .left
          .map(failure => MalformedTypingResult(failure.getMessage()))
          .map { typ =>
            UIValueParameter(
              name = parameter.name,
              typ = typ,
              expression = parameter.expression
            )
          }
      }.sequence
      variableTypes <- decodeVariableTypes(request.variableTypes, typingResultDecoder)
    } yield ParametersValidationRequest(parameters, variableTypes)
  }

}
