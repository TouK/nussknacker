package pl.touk.nussknacker.ui.services

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.ui.additionalInfo.AdditionalInfoProviders
import pl.touk.nussknacker.ui.api.NodesApiEndpoints.Dtos.TypingResultDto.{toTypingResult, typingResultToDto}
import pl.touk.nussknacker.ui.api.NodesApiEndpoints.Dtos.{
  ExpressionSuggestionDto,
  NodeValidationResultDto,
  ParameterDto,
  ParametersValidationResultDto
}
import pl.touk.nussknacker.ui.api.NodesApiEndpoints
import pl.touk.nussknacker.ui.api.NodesApiEndpoints.{NodeValidationRequest, ParametersValidationRequest}
import pl.touk.nussknacker.ui.process.ProcessService.GetScenarioWithDetailsOptions
import pl.touk.nussknacker.ui.process.{ProcessCategoryService, ProcessService}
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.AuthenticationResources
import pl.touk.nussknacker.ui.suggester.ExpressionSuggester
import pl.touk.nussknacker.ui.validation.{NodeValidator, ParametersValidator, UIProcessValidator}

import scala.concurrent.{ExecutionContext, Future}

class NodesApiHttpService(
    config: Config,
    authenticator: AuthenticationResources,
    getProcessCategoryService: () => ProcessCategoryService,
    typeToConfig: ProcessingTypeDataProvider[ModelData, _],
    typeToProcessValidator: ProcessingTypeDataProvider[UIProcessValidator, _],
    typeToNodeValidator: ProcessingTypeDataProvider[NodeValidator, _],
    typeToExpressionSuggester: ProcessingTypeDataProvider[ExpressionSuggester, _],
    typeToParametersValidator: ProcessingTypeDataProvider[ParametersValidator, _],
    protected val scenarioService: ProcessService
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(config, getProcessCategoryService, authenticator)
    with LazyLogging {

  private val nodesApiEndpoints = new NodesApiEndpoints(authenticator.authenticationMethod())

  private val additionalInfoProviders = new AdditionalInfoProviders(typeToConfig)

  expose {
    nodesApiEndpoints.nodesAdditionalInfoEndpoint
      .serverSecurityLogic(authorizeKnownUser[String])
      .serverLogic { implicit loggedUser => pair =>
        val (scenarioName, nodeData) = pair

        scenarioService
          .getProcessId(scenarioName)
          .flatMap { scenarioId =>
            scenarioService
              .getLatestProcessWithDetails(
                ProcessIdWithName(scenarioId, scenarioName),
                GetScenarioWithDetailsOptions.detailsOnly
              )
              .flatMap { scenario =>
                additionalInfoProviders
                  .prepareAdditionalInfoForNode(nodeData, scenario.processingType)
                  .map { additionalInfo =>
                    success(additionalInfo)
                  }
              }
          }
          .recover { case _ =>
            businessError(s"No scenario $scenarioName found")
          }
      }
  }

  expose {
    nodesApiEndpoints.nodesValidationEndpoint
      .serverSecurityLogic(authorizeKnownUser[String])
      .serverLogic { implicit loggedUser => pair =>
        val (scenarioName, nodeValidationRequestDto) = pair

        scenarioService
          .getProcessId(scenarioName)
          .flatMap { scenarioId =>
            scenarioService
              .getLatestProcessWithDetails(
                ProcessIdWithName(scenarioId, scenarioName),
                GetScenarioWithDetailsOptions.detailsOnly
              )
              .flatMap { scenario =>
                implicit val modelData: ModelData = typeToConfig.forTypeUnsafe(scenario.processingType)
                val nodeValidator                 = typeToNodeValidator.forTypeUnsafe(scenario.processingType)
                val nodeData                      = NodeValidationRequest.apply(nodeValidationRequestDto)
                Future(success(NodeValidationResultDto.apply(nodeValidator.validate(scenarioName, nodeData))))
              }
          }
          .recover { case e: Throwable =>
            businessError(e.getMessage)
          }
      }
  }

  expose {
    nodesApiEndpoints.propertiesAdditionalInfoEndpoint
      .serverSecurityLogic(authorizeKnownUser[String])
      .serverLogic { implicit loggedUser => pair =>
        val (scenarioName, scenarioProperties) = pair

        scenarioService
          .getProcessId(scenarioName)
          .flatMap { scenarioId =>
            scenarioService
              .getLatestProcessWithDetails(
                ProcessIdWithName(scenarioId, scenarioName),
                GetScenarioWithDetailsOptions.detailsOnly
              )
              .flatMap { scenario =>
                additionalInfoProviders
                  .prepareAdditionalInfoForProperties(
                    scenarioProperties.toMetaData(scenarioName),
                    scenario.processingType
                  )
                  .map { additionalInfo =>
                    success(additionalInfo)
                  }
              }
          }
          .recover { case _ =>
            businessError(s"No scenario $scenarioName found")
          }
      }
  }

  expose {
    nodesApiEndpoints.propertiesValidationEndpoint
      .serverSecurityLogic(authorizeKnownUser[String])
      .serverLogic { implicit loggedUser => pair =>
        val (scenarioName, request) = pair

        scenarioService
          .getProcessId(scenarioName)
          .flatMap { scenarioId =>
            scenarioService
              .getLatestProcessWithDetails(
                ProcessIdWithName(scenarioId, scenarioName),
                GetScenarioWithDetailsOptions.detailsOnly
              )
              .flatMap { scenarioWithDetails =>
                val scenario = DisplayableProcess(
                  request.name,
                  ProcessProperties(request.additionalFields),
                  Nil,
                  Nil,
                  scenarioWithDetails.processingType,
                  scenarioWithDetails.processCategory
                )
                val result =
                  typeToProcessValidator
                    .forTypeUnsafe(scenario.processingType)
                    .validate(scenario)
                Future(
                  success(
                    NodeValidationResultDto(
                      parameters = None,
                      expressionType = None,
                      validationErrors = result.errors.processPropertiesErrors,
                      validationPerformed = true
                    )
                  )
                )
              }
          }
          .recover { case _ =>
            businessError(s"No scenario $scenarioName found")
          }
      }
  }

  expose {
    nodesApiEndpoints.parametersValidationEndpoint
      .serverSecurityLogic(authorizeKnownUser[String])
      .serverLogic { implicit loggedUser => pair =>
        val (processingType, request) = pair

        try {
          implicit val modelData: ModelData = typeToConfig.forTypeUnsafe(processingType)
          val validator                     = typeToParametersValidator.forTypeUnsafe(processingType)
          val requestWithTypingResult       = ParametersValidationRequest.apply(request)
          val validationResults             = validator.validate(requestWithTypingResult)

          Future(
            success(
              ParametersValidationResultDto(validationResults, validationPerformed = true)
            )
          )
        } catch {
          case _: IllegalArgumentException =>
            Future(businessError(s"ProcessingType type: $processingType not found"))
        }
      }
  }

  expose {
    nodesApiEndpoints.parametersSuggestionsEndpoint
      .serverSecurityLogic(authorizeKnownUser[String])
      .serverLogic { implicit loggedUser => pair =>
        val (processingType, request) = pair

        try {
          implicit val modelData: ModelData = typeToConfig.forTypeUnsafe(processingType)
          val expressionSuggester           = typeToExpressionSuggester.forTypeUnsafe(processingType)
          expressionSuggester
            .expressionSuggestions(
              request.expression,
              request.caretPosition2d,
              request.variableTypes.map { case (key, result) => (key, toTypingResult(result)) }
            )
            .map { suggestion =>
              success(
                suggestion.map { suggest =>
                  ExpressionSuggestionDto(
                    suggest.methodName,
                    typingResultToDto(suggest.refClazz),
                    suggest.fromClass,
                    suggest.description,
                    suggest.parameters.map { param => ParameterDto(param.name, typingResultToDto(param.refClazz)) }
                  )
                }
              )
            }
        } catch {
          case _: IllegalArgumentException =>
            Future(businessError(s"ProcessingType type: $processingType not found"))
        }

      }
  }

}
