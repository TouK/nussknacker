package pl.touk.nussknacker.ui.services

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.circe.DecodingFailure
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.graph.{ProcessProperties, ScenarioGraph}
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessingType}
import pl.touk.nussknacker.ui.additionalInfo.AdditionalInfoProviders
import pl.touk.nussknacker.ui.api.NodesApiEndpoints.Dtos.{
  ExpressionSuggestionDto,
  NodeValidationResultDto,
  ParametersValidationResultDto,
  prepareTypingResultDecoder
}
import pl.touk.nussknacker.ui.api.NodesApiEndpoints
import pl.touk.nussknacker.ui.api.NodesApiEndpoints.{
  NodeValidationRequest,
  ParametersValidationRequest,
  mapVariableTypesOrThrowError
}
import pl.touk.nussknacker.ui.process.ProcessService.GetScenarioWithDetailsOptions
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, LoggedUser}
import pl.touk.nussknacker.ui.suggester.ExpressionSuggester
import pl.touk.nussknacker.ui.validation.{NodeValidator, ParametersValidator, UIProcessValidator}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

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

  private val nodesApiEndpoints = new NodesApiEndpoints(authenticator.authenticationMethod())

  private val additionalInfoProviders = new AdditionalInfoProviders(typeToConfig)

  expose {
    nodesApiEndpoints.nodesAdditionalInfoEndpoint
      .serverSecurityLogic(authorizeKnownUser[String])
      .serverLogic { implicit loggedUser =>
        { case (scenarioName, nodeData) =>
          val result = for {
            scenarioId <- scenarioService.getProcessId(scenarioName)
            scenario <- scenarioService.getLatestProcessWithDetails(
              ProcessIdWithName(scenarioId, scenarioName),
              GetScenarioWithDetailsOptions.detailsOnly
            )
            additionalInfo <- additionalInfoProviders.prepareAdditionalInfoForNode(nodeData, scenario.processingType)
          } yield success(additionalInfo)
          result.recover { case _: ProcessNotFoundError =>
            businessError(s"No scenario $scenarioName found")
          }
        }
      }
  }

  expose {
    nodesApiEndpoints.nodesValidationEndpoint
      .serverSecurityLogic(authorizeKnownUser[String])
      .serverLogic { implicit loggedUser =>
        { case (scenarioName, nodeValidationRequestDto) =>
          val result = for {
            scenarioId <- scenarioService.getProcessId(scenarioName)
            scenario <- scenarioService.getLatestProcessWithDetails(
              ProcessIdWithName(scenarioId, scenarioName),
              GetScenarioWithDetailsOptions.detailsOnly
            )
            modelData: ModelData = typeToConfig.forTypeUnsafe(scenario.processingType)
            nodeValidator        = typeToNodeValidator.forTypeUnsafe(scenario.processingType)
            nodeData   = NodeValidationRequest.apply(nodeValidationRequestDto)(prepareTypingResultDecoder(modelData))
            validation = NodeValidationResultDto.apply(nodeValidator.validate(scenarioName, nodeData))
          } yield success(validation)
          result.recover {
            case e: ProcessNotFoundError =>
              businessError(e.getMessage)
            case e: DecodingFailure =>
              businessError("The request content was malformed:\n" + e.getMessage)
          }
        }
      }
  }

  expose {
    nodesApiEndpoints.propertiesAdditionalInfoEndpoint
      .serverSecurityLogic(authorizeKnownUser[String])
      .serverLogic { implicit loggedUser =>
        { case (scenarioName, scenarioProperties) =>
          val result = for {
            scenarioId <- scenarioService.getProcessId(scenarioName)
            scenario <- scenarioService.getLatestProcessWithDetails(
              ProcessIdWithName(scenarioId, scenarioName),
              GetScenarioWithDetailsOptions.detailsOnly
            )
            additionalInfo <- additionalInfoProviders.prepareAdditionalInfoForProperties(
              scenarioProperties.toMetaData(scenarioName),
              scenario.processingType
            )
          } yield success(additionalInfo)
          result.recover { case _: ProcessNotFoundError =>
            businessError(s"No scenario $scenarioName found")
          }
        }
      }
  }

  expose {
    nodesApiEndpoints.propertiesValidationEndpoint
      .serverSecurityLogic(authorizeKnownUser[String])
      .serverLogic { implicit loggedUser =>
        { case (scenarioName, request) =>
          val result = for {
            scenarioId <- scenarioService.getProcessId(scenarioName)
            scenarioWithDetails <- scenarioService.getLatestProcessWithDetails(
              ProcessIdWithName(scenarioId, scenarioName),
              GetScenarioWithDetailsOptions.detailsOnly
            )
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
          } yield success(validation)
          result.recover { case _: ProcessNotFoundError =>
            businessError(s"No scenario $scenarioName found")
          }
        }
      }
  }

  expose {
    nodesApiEndpoints.parametersValidationEndpoint
      .serverSecurityLogic(authorizeKnownUser[String])
      .serverLogic { implicit loggedUser =>
        { case (processingType, request) =>
          val result = for {
            modelData: ModelData <- Try(typeToConfig.forTypeUnsafe(processingType))
            validator = typeToParametersValidator.forTypeUnsafe(processingType)
            requestWithTypingResult =
              ParametersValidationRequest.apply(request)(prepareTypingResultDecoder(modelData))
            validationResults = validator.validate(requestWithTypingResult)
          } yield success(ParametersValidationResultDto(validationResults, validationPerformed = true))
          result match {
            case Success(response) => Future(response)
            case Failure(_: IllegalArgumentException) =>
              Future(businessError(s"ProcessingType type: $processingType not found"))
            case Failure(e: DecodingFailure) =>
              Future(businessError("The request content was malformed:\n" + e.getMessage))
            case Failure(exception) => throw exception
          }
        }
      }
  }

  expose {
    nodesApiEndpoints.parametersSuggestionsEndpoint
      .serverSecurityLogic(authorizeKnownUser[String])
      .serverLogic { implicit loggedUser =>
        { case (processingType, request) =>
          val result = for {
            modelData: ModelData <- Try(typeToConfig.forTypeUnsafe(processingType))
            expressionSuggester = typeToExpressionSuggester.forTypeUnsafe(processingType)
            suggestions = expressionSuggester.expressionSuggestions(
              request.expression,
              request.caretPosition2d,
              mapVariableTypesOrThrowError(request.variableTypes, prepareTypingResultDecoder(modelData))
            )
          } yield suggestions
          result match {
            case Success(result) =>
              result.map(value => success(value.map(expression => ExpressionSuggestionDto(expression))))
            case Failure(_: IllegalArgumentException) =>
              Future(businessError(s"ProcessingType type: $processingType not found"))
            case Failure(e: DecodingFailure) =>
              Future(businessError("The request content was malformed:\n" + e.getMessage))
            case Failure(exception) => throw exception
          }
        }
      }
  }

}
