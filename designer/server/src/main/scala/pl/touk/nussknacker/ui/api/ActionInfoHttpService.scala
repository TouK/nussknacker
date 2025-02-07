package pl.touk.nussknacker.ui.api

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.ui.api.ActionInfoHttpService.{ActionInfoError, toUiParameters}
import pl.touk.nussknacker.ui.api.ActionInfoHttpService.ActionInfoError.{NoPermission, NoScenario}
import pl.touk.nussknacker.ui.api.BaseHttpService.CustomAuthorizationError
import pl.touk.nussknacker.ui.api.description.ActionInfoEndpoints
import pl.touk.nussknacker.ui.api.description.ActionInfoEndpoints.Dtos.{
  UiActionNodeParametersDto,
  UiActionParameterConfigDto,
  UiActionParametersDto
}
import pl.touk.nussknacker.ui.api.utils.ScenarioHttpServiceExtensions
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.ProcessService.GetScenarioWithDetailsOptions
import pl.touk.nussknacker.ui.process.deployment.ActionInfoService
import pl.touk.nussknacker.ui.process.deployment.ActionInfoService.UiActionParameters
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.AuthManager
import sttp.tapir.{Codec, CodecFormat}

import scala.concurrent.ExecutionContext

class ActionInfoHttpService(
    authManager: AuthManager,
    processingTypeToActionInfoService: ProcessingTypeDataProvider[ActionInfoService, _],
    protected override val scenarioService: ProcessService
)(implicit val executionContext: ExecutionContext)
    extends BaseHttpService(authManager)
    with ScenarioHttpServiceExtensions
    with LazyLogging {

  override protected type BusinessErrorType = ActionInfoError
  override protected def noScenarioError(scenarioName: ProcessName): ActionInfoError      = NoScenario(scenarioName)
  override protected def noPermissionError: ActionInfoError with CustomAuthorizationError = NoPermission

  private val securityInput = authManager.authenticationEndpointInput()

  private val endpoints = new ActionInfoEndpoints(securityInput)

  expose {
    endpoints.actionParametersEndpoint
      .serverSecurityLogic(authorizeKnownUser[ActionInfoError])
      .serverLogicEitherT { implicit loggedUser => actionParametersInput =>
        val scenarioName = actionParametersInput
        for {
          scenarioWithDetails <- getScenarioWithDetailsByName(
            scenarioName,
            GetScenarioWithDetailsOptions.withScenarioGraph
          )
          actionInfoService = processingTypeToActionInfoService.forProcessingTypeUnsafe(
            scenarioWithDetails.processingType
          )
          actionNodeParameters = actionInfoService.getActionParameters(
            scenarioWithDetails.scenarioGraphUnsafe,
            scenarioWithDetails.processVersionUnsafe,
            scenarioWithDetails.isFragment
          )
        } yield toUiParameters(actionNodeParameters)
      }
  }

}

object ActionInfoHttpService {

  sealed trait ActionInfoError

  object ActionInfoError {
    final case class NoScenario(scenarioName: ProcessName) extends ActionInfoError
    final case object NoPermission                         extends ActionInfoError with CustomAuthorizationError

    implicit val noScenarioCodec: Codec[String, NoScenario, CodecFormat.TextPlain] = {
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NoScenario](e => s"No scenario ${e.scenarioName} found")
    }

  }

  private def toUiParameters(actionNodeParameters: UiActionParameters): UiActionParametersDto = {
    val parameters = actionNodeParameters.actionNameToParameters.map { case (scenarioActionName, nodeParamsMap) =>
      scenarioActionName -> nodeParamsMap.map { nodeParams =>
        UiActionNodeParametersDto(
          nodeParams.nodeId,
          nodeParams.parameters.map { case (name, config) =>
            name -> UiActionParameterConfigDto(
              config.defaultValue,
              config.editor,
              config.label,
              config.hintText
            )
          }
        )
      }
    }
    UiActionParametersDto(parameters)
  }

}
