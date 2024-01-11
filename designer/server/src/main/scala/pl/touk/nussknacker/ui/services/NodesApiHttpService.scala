package pl.touk.nussknacker.ui.services

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.graph.{ProcessProperties, ScenarioGraph}
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.ui.additionalInfo.AdditionalInfoProviders
import pl.touk.nussknacker.ui.api.NodesApiEndpoints.Dtos.TypingResultDto.{toTypingResult, typingResultToDto}
import pl.touk.nussknacker.ui.api.NodesApiEndpoints.Dtos.{ExpressionSuggestionDto, NodeValidationRequestDto, NodeValidationResultDto, ParameterDto, ParametersValidationRequestDto, ParametersValidationResultDto}
import pl.touk.nussknacker.ui.api.NodesApiEndpoints
import pl.touk.nussknacker.ui.api.NodesApiEndpoints.NodeValidationResult
import pl.touk.nussknacker.ui.process.ProcessService.GetScenarioWithDetailsOptions
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.AuthenticationResources
import pl.touk.nussknacker.ui.suggester.ExpressionSuggester
import pl.touk.nussknacker.ui.validation.{NodeValidator, ParametersValidator, UIProcessValidator}

import scala.concurrent.{ExecutionContext, Future}

class NodesApiHttpService(
    config: Config,
    authenticator: AuthenticationResources,
    typeToConfig: ProcessingTypeDataProvider[ModelData, _],
    typeToProcessValidator: ProcessingTypeDataProvider[UIProcessValidator, _],
    typeToNodeValidator: ProcessingTypeDataProvider[NodeValidator, _],
    typeToExpressionSuggester: ProcessingTypeDataProvider[ExpressionSuggester, _],
    typeToParametersValidator: ProcessingTypeDataProvider[ParametersValidator, _],
    protected val processService: ProcessService
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(config, authenticator)
    with LazyLogging {

  private val nodesApiEndpoints = new NodesApiEndpoints(authenticator.authenticationMethod())

  private val additionalInfoProviders = new AdditionalInfoProviders(typeToConfig)

  expose {
    nodesApiEndpoints.nodesAdditionalInfoEndpoint
      .serverSecurityLogic(authorizeKnownUser[String])
      .serverLogic { user => pair =>
        val (processName, nodeData) = pair

        processService
          .getProcessId(processName)
          .flatMap { processId =>
            processService
              .getLatestProcessWithDetails(
                ProcessIdWithName(processId, processName),
                GetScenarioWithDetailsOptions.detailsOnly
              )(user)
              .flatMap { process =>
                additionalInfoProviders
                  .prepareAdditionalInfoForNode(nodeData, process.processingType)(executionContext, user)
                  .map { additionalInfo =>
                    success(additionalInfo)
                  }
              }
          }
          .recover { case _ =>
            businessError(s"No scenario $processName found")
          }
      }
  }

  expose {
    nodesApiEndpoints.nodesValidationEndpoint
      .serverSecurityLogic(authorizeKnownUser[String])
      .serverLogic { user => pair =>
        val (processName, nodeValidationRequestDto) = pair

        processService
          .getProcessId(processName)
          .flatMap { processId =>
            processService
              .getLatestProcessWithDetails(
                ProcessIdWithName(processId, processName),
                GetScenarioWithDetailsOptions.detailsOnly
              )(user)
              .flatMap { process =>
                println(process)
                val nodeValidator = typeToNodeValidator.forTypeUnsafe(process.processingType)(user)
                implicit val modelData: ModelData = typeToConfig.forTypeUnsafe(process.processingType)(user)
                val nodeValidator                 = typeToNodeValidator.forTypeUnsafe(process.processingType)(user)
                NodeValidationRequestDto.toRequest(nodeValidationRequestDto) match {
                  case Some(nodeData) =>
                    Future(success(NodeValidationResult.toDto(nodeValidator.validate(processName, nodeData)(user))))
                  case None =>
                    Future(businessError("None"))
                }

              }
          }
          .recover { case _ =>
            businessError(s"No scenario $processName found")
          }
      }
  }

  expose {
    nodesApiEndpoints.propertiesAdditionalInfoEndpoint
      .serverSecurityLogic(authorizeKnownUser[String])
      .serverLogic { user => pair =>
        val (processName, processProperties) = pair

        processService
          .getProcessId(processName)
          .flatMap { processId =>
            processService
              .getLatestProcessWithDetails(
                ProcessIdWithName(processId, processName),
                GetScenarioWithDetailsOptions.detailsOnly
              )(user)
              .flatMap { process =>
                additionalInfoProviders
                  .prepareAdditionalInfoForProperties(
                    processProperties.toMetaData(processName),
                    process.processingType
                  )(executionContext, user)
                  .map { additionalInfo =>
                    success(additionalInfo)
                  }
              }
          }
          .recover { case _ =>
            businessError(s"No scenario $processName found")
          }
      }
  }

  expose {
    nodesApiEndpoints.propertiesValidationEndpoint
      .serverSecurityLogic(authorizeKnownUser[String])
      .serverLogic { user => pair =>
        val (processName, request) = pair

        processService
          .getProcessId(processName)
          .flatMap { processId =>
            processService
              .getLatestProcessWithDetails(
                ProcessIdWithName(processId, processName),
                GetScenarioWithDetailsOptions.detailsOnly
              )(user)
              .flatMap { process =>
                val scenario = ScenarioGraph(
                  ProcessProperties(request.additionalFields),
                  Nil,
                  Nil
                )
                val result =
                  typeToProcessValidator
                    .forTypeUnsafe(process.processingType)(user)
                    .validate(scenario, processName, isFragment = false)(user)
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
            businessError(s"No scenario $processName found")
          }
      }
  }

  expose {
    nodesApiEndpoints.parametersValidationEndpoint
      .serverSecurityLogic(authorizeKnownUser[String])
      .serverLogic { user => pair =>
        val (processingType, request) = pair

        try {
          implicit val modelData: ModelData = typeToConfig.forTypeUnsafe(processingType)(user)
          val validator                     = typeToParametersValidator.forTypeUnsafe(processingType)(user)
          val requestWithTypingResult       = ParametersValidationRequestDto.withoutDto(request)(modelData)
          val validationResults             = validator.validate(requestWithTypingResult)

          Future(
            success(
              ParametersValidationResultDto(validationResults, validationPerformed = true)
            )
          )
        } catch {
          case _: Throwable =>
            Future(businessError(s"ProcessingType type: $processingType not found"))
        }
      }
  }

  expose {
    nodesApiEndpoints.parametersSuggestionsEndpoint
      .serverSecurityLogic(authorizeKnownUser[String])
      .serverLogic { user => pair =>
        val (processingType, request) = pair

        try {
          implicit val modelData: ModelData = typeToConfig.forTypeUnsafe(processingType)(user)
          val expressionSuggester           = typeToExpressionSuggester.forTypeUnsafe(processingType)(user)
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
          case _: Throwable =>
            Future(businessError(s"ProcessingType type: $processingType not fond"))
        }

      }
  }

}
