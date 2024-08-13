package pl.touk.nussknacker.ui.api

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.api.description.ScenarioParametersApiEndpoints
import pl.touk.nussknacker.ui.api.description.ScenarioParametersApiEndpoints.Dtos.ScenarioParametersCombinationWithEngineErrors
import pl.touk.nussknacker.ui.process.processingtype.ScenarioParametersService
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.{AuthManager, LoggedUser}

import scala.concurrent.{ExecutionContext, Future}

class ScenarioParametersApiHttpService(
    authManager: AuthManager,
    scenarioParametersService: ProcessingTypeDataProvider[_, ScenarioParametersService]
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(authManager)
    with LazyLogging {

  private val parametersApiEndpoints = new ScenarioParametersApiEndpoints(
    authManager.authenticationEndpointInput()
  )

  expose {
    parametersApiEndpoints.scenarioParametersCombinationsEndpoint
      .serverSecurityLogic(authorizeKnownUser[Unit])
      .serverLogicSuccess { implicit loggedUser: LoggedUser => _ =>
        Future {
          val service = scenarioParametersService.combined
          val parametersCombination =
            service.scenarioParametersCombinationsWithWritePermission
              .sortBy(parameters =>
                (parameters.processingMode.toJsonString, parameters.category, parameters.engineSetupName.value)
              )
          val engineSetupErrors = service.engineSetupErrorsWithWritePermission.filterNot(_._2.isEmpty)
          ScenarioParametersCombinationWithEngineErrors(
            combinations = parametersCombination,
            engineSetupErrors = engineSetupErrors
          )
        }
      }
  }

}
