package pl.touk.nussknacker.ui.services

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.api.ScenarioParametersApiEndpoints
import pl.touk.nussknacker.ui.api.ScenarioParametersApiEndpoints.Dtos.ScenarioParametersCombinationWithEngineErrors
import pl.touk.nussknacker.ui.process.processingtype.{ProcessingTypeDataProvider, ScenarioParametersService}
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, LoggedUser}

import scala.concurrent.{ExecutionContext, Future}

class ScenarioParametersApiHttpService(
    config: Config,
    authenticator: AuthenticationResources,
    scenarioParametersService: ProcessingTypeDataProvider[_, ScenarioParametersService]
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(config, authenticator)
    with LazyLogging {

  private val parametersApiEndpoints = new ScenarioParametersApiEndpoints(authenticator.authenticationMethod())

  expose {
    parametersApiEndpoints.scenarioParametersCombinationsEndpoint
      .serverSecurityLogic(authorizeKnownUser[Unit])
      .serverLogicSuccess { implicit loggedUser: LoggedUser => _ =>
        Future {
          val service = scenarioParametersService.combined
          val parametersCombination =
            service.scenarioParametersCombinationsWithWritePermission
              .sortBy(parameters =>
                (parameters.processingMode.value, parameters.category, parameters.engineSetupName.value)
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
