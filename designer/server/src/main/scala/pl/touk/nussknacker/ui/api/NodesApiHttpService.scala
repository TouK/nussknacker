package pl.touk.nussknacker.ui.api

import cats.data.EitherT
import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.LazyLogging
import io.circe.Decoder
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.graph.{ProcessProperties, ScenarioGraph}
import pl.touk.nussknacker.engine.api.process.{ProcessingType, ProcessName}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.graph.node.SourceNodeData
import pl.touk.nussknacker.engine.spel.ExpressionSuggestion
import pl.touk.nussknacker.restmodel.definition.UIValueParameter
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.ui.additionalInfo.AdditionalInfoProviders
import pl.touk.nussknacker.ui.api.BaseHttpService.CustomAuthorizationError
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.{
  decodeVariableTypes,
  prepareTypingResultDecoder,
  ExpressionSuggestionDto,
  NodesError,
  NodeValidationRequest,
  NodeValidationRequestDto,
  NodeValidationResult,
  NodeValidationResultDto,
  ParametersValidationRequest,
  ParametersValidationRequestDto,
  ParametersValidationResultDto
}
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.NodesError.BadRequestNodesError.{
  InvalidNodeType,
  MalformedTypingResult,
  Serialization,
  SourceCompilation,
  TooManySamplesRequested,
  UnsupportedSourcePreview
}
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.NodesError.ForbiddenNodesError.NoPermission
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.NodesError.NotFoundNodesError.{
  NoDataGenerated,
  NoProcessingType,
  NoScenario
}
import pl.touk.nussknacker.ui.api.utils.ScenarioDetailsOps._
import pl.touk.nussknacker.ui.api.utils.ScenarioHttpServiceExtensions
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.process.test.ScenarioTestService
import pl.touk.nussknacker.ui.process.test.ScenarioTestService.SourceTestError
import pl.touk.nussknacker.ui.process.test.ScenarioTestService.SourceTestError._
import pl.touk.nussknacker.ui.security.api.{AuthManager, LoggedUser}
import pl.touk.nussknacker.ui.suggester.ExpressionSuggester
import pl.touk.nussknacker.ui.validation.{NodeValidator, ParametersValidator, UIProcessValidator}

import scala.concurrent.{ExecutionContext, Future}

class NodesApiHttpService(
    authManager: AuthManager,
    processingTypeToConfig: ProcessingTypeDataProvider[ModelData, _],
    processingTypeToProcessValidator: ProcessingTypeDataProvider[UIProcessValidator, _],
    processingTypeToNodeValidator: ProcessingTypeDataProvider[NodeValidator, _],
    processingTypeToExpressionSuggester: ProcessingTypeDataProvider[ExpressionSuggester, _],
    processingTypeToParametersValidator: ProcessingTypeDataProvider[ParametersValidator, _],
    processingTypeToScenarioTestServices: ProcessingTypeDataProvider[ScenarioTestService, _],
    protected override val scenarioService: ProcessService
)(override protected implicit val executionContext: ExecutionContext)
    extends BaseHttpService(authManager)
    with ScenarioHttpServiceExtensions
    with LazyLogging {

  override protected type BusinessErrorType = NodesError
  override protected def noScenarioError(scenarioName: ProcessName): NodesError      = NoScenario(scenarioName)
  override protected def noPermissionError: NodesError with CustomAuthorizationError = NoPermission

  private val nodesApiEndpoints = new NodesApiEndpoints(authManager.authenticationEndpointInput())

  private val additionalInfoProviders = new AdditionalInfoProviders(processingTypeToConfig)

  expose {
    nodesApiEndpoints.nodesAdditionalInfoEndpoint
      .serverSecurityLogic(authorizeKnownUser[NodesError])
      .serverLogicEitherT { implicit loggedUser =>
        { case (scenarioName, nodeData) =>
          for {
            scenario <- getScenarioWithDetailsByName(scenarioName)
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
            scenario  <- getScenarioWithDetailsByName(scenarioName)
            modelData <- getModelData(scenario.processingType)
            nodeValidator = processingTypeToNodeValidator.forProcessingTypeUnsafe(scenario.processingType)
            nodeData   <- dtoToNodeRequest(nodeValidationRequestDto, modelData)
            validation <- getNodeValidation(nodeValidator, scenario, nodeData)
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
            scenario <- getScenarioWithDetailsByName(scenarioName)
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
            scenarioWithDetails <- getScenarioWithDetailsByName(scenarioName)
            scenario = ScenarioGraph(ProcessProperties(request.additionalFields), Nil, Nil)
            result = processingTypeToProcessValidator
              .forProcessingTypeUnsafe(scenarioWithDetails.processingType)
              .validate(
                scenario,
                request.name,
                scenarioWithDetails.isFragment,
                scenarioWithDetails.scenarioLabels,
              )
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
    nodesApiEndpoints.recordsEndpoint
      .serverSecurityLogic(authorizeKnownUser[NodesError])
      .serverLogicEitherT { implicit loggedUser =>
        { case (scenarioName, numberOfRecords, recordsRequestDto) =>
          for {
            scenarioWithDetails <- getScenarioWithDetailsByName(scenarioName)
            scenarioTestService = processingTypeToScenarioTestServices.forProcessingTypeUnsafe(
              scenarioWithDetails.processingType
            )
            sourceNodeData <- EitherT.fromEither[Future](recordsRequestDto.nodeData match {
              case source: SourceNodeData => Right(source)
              case other =>
                Left(InvalidNodeType("SourceNodeData", other.getClass.getSimpleName))
            })
            parametersDefinition <- EitherT[Future, NodesError, String](
              // The service is expected to limit the number of returned records if the response is too large.
              // If it does not, we may need to implement client-side pagination or request size limits.
              scenarioTestService.getDataFromSource(
                recordsRequestDto.processProperties.toMetaData(scenarioName),
                sourceNodeData,
                numberOfRecords
              ) match {
                case Left(SourceCompilationError(nodeId, errors)) =>
                  Future(Left(SourceCompilation(nodeId, errors)))
                case Left(UnsupportedSourcePreviewError(nodeId)) =>
                  Future(Left(UnsupportedSourcePreview(nodeId)))
                case Left(NoDataGeneratedError) =>
                  Future(Left(NoDataGenerated))
                case Left(SerializationError(message)) =>
                  Future(Left(Serialization(message)))
                case Left(TooManySamplesRequestedError(maxSamples)) =>
                  Future(Left(TooManySamplesRequested(maxSamples)))
                case Right(rawScenarioTestData) =>
                  Future(Right(rawScenarioTestData.content))
              }
            )
          } yield parametersDefinition
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
            validator = processingTypeToParametersValidator.forProcessingTypeUnsafe(processingType)
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
            expressionSuggester = processingTypeToExpressionSuggester.forProcessingTypeUnsafe(processingType)
            suggestions <- getSuggestions(expressionSuggester, request, modelData)
            suggestionDto = suggestions.map(expression => ExpressionSuggestionDto(expression))
          } yield suggestionDto
        }
      }
  }

  private def dtoToNodeRequest(
      nodeValidationRequestDto: NodeValidationRequestDto,
      modelData: ModelData
  ): EitherT[Future, NodesError, NodeValidationRequest] = {
    EitherT.fromEither(
      fromNodeRequestDto(nodeValidationRequestDto)(prepareTypingResultDecoder(modelData.modelClassLoader))
    )
  }

  private def getModelData(
      processingType: ProcessingType
  )(implicit user: LoggedUser): EitherT[Future, NodesError, ModelData] = {
    EitherT.fromEither(
      processingTypeToConfig
        .forProcessingTypeE(processingType)
        .left
        .map(_ => NoPermission)
        .flatMap(_.toRight(NoProcessingType(processingType)))
    )
  }

  private def getNodeValidation(
      nodeValidator: NodeValidator,
      scenario: ScenarioWithDetails,
      nodeData: NodeValidationRequest
  )(
      implicit user: LoggedUser
  ): EitherT[Future, NodesError, NodeValidationResult] =
    EitherT.fromEither(
      try {
        Right(nodeValidator.validate(scenario.processVersionUnsafe, nodeData))
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
          prepareTypingResultDecoder(modelData.modelClassLoader)
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
    val typingResultDecoder = prepareTypingResultDecoder(modelData.modelClassLoader)
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
