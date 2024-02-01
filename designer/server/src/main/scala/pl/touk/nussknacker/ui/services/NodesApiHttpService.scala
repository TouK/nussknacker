package pl.touk.nussknacker.ui.services

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.graph.{ProcessProperties, ScenarioGraph}
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessingType}
import pl.touk.nussknacker.ui.additionalInfo.AdditionalInfoProviders
import pl.touk.nussknacker.ui.api.NodesApiEndpoints.Dtos.{
  ExpressionSuggestionDto,
  NodeValidationResultDto,
  ParameterDto,
  ParametersValidationResultDto,
  prepareTypingResultDecoder
}
import pl.touk.nussknacker.ui.api.NodesApiEndpoints
import pl.touk.nussknacker.ui.api.NodesApiEndpoints.{NodeValidationRequest, ParametersValidationRequest}
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
          result.recover { case e: ProcessNotFoundError =>
            businessError(e.getMessage)
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
          tryGetModelData(typeToConfig, processingType) match {
            case Success(value) =>
              implicit val modelData: ModelData = value
              val validator                     = typeToParametersValidator.forTypeUnsafe(processingType)
              val requestWithTypingResult =
                ParametersValidationRequest.apply(request)(prepareTypingResultDecoder(modelData))
              val validationResults = validator.validate(requestWithTypingResult)
              Future(
                success(
                  ParametersValidationResultDto(validationResults, validationPerformed = true)
                )
              )
            case Failure(_: IllegalArgumentException) =>
              Future(businessError(s"ProcessingType type: $processingType not found"))
            case Failure(exception) => Future(businessError(exception.getMessage))
          }
        }
      }
  }

  expose {
    nodesApiEndpoints.parametersSuggestionsEndpoint
      .serverSecurityLogic(authorizeKnownUser[String])
      .serverLogic { implicit loggedUser =>
        { case (processingType, request) =>
          tryGetModelData(typeToConfig, processingType) match {
            case Success(value) =>
              implicit val modelData: ModelData = value
              val expressionSuggester           = typeToExpressionSuggester.forTypeUnsafe(processingType)
              expressionSuggester
                .expressionSuggestions(
                  request.expression,
                  request.caretPosition2d,
                  request.decodedVariableTypes(prepareTypingResultDecoder(modelData))
                )
                .map { suggestions =>
                  success(
                    suggestions.map { suggest =>
                      ExpressionSuggestionDto(
                        suggest.methodName,
                        suggest.refClazz,
                        suggest.fromClass,
                        suggest.description,
                        suggest.parameters.map { param => ParameterDto(param.name, param.refClazz) }
                      )
                    }
                  )
                }
            case Failure(_: IllegalArgumentException) =>
              Future(businessError(s"ProcessingType type: $processingType not found"))
            case Failure(exception) => Future(Left(Left(exception.getMessage)))
          }
        }
      }
  }

  private def tryGetModelData(typeToConfig: ProcessingTypeDataProvider[ModelData, _], processingType: ProcessingType)(
      implicit loggedUser: LoggedUser
  ): Try[ModelData] = {
    Try(typeToConfig.forTypeUnsafe(processingType))
  }

}
