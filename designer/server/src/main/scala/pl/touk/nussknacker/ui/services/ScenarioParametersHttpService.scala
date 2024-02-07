package pl.touk.nussknacker.ui.services

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.api.ScenarioParametersEndpoints
import pl.touk.nussknacker.ui.api.ScenarioParametersEndpoints.Dtos
import pl.touk.nussknacker.ui.api.ScenarioParametersEndpoints.Dtos.EngineSetupDetails
import pl.touk.nussknacker.ui.process.processingtype.{ProcessingTypeDataProvider, ScenarioParametersService}
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, LoggedUser}

import scala.concurrent.{ExecutionContext, Future}

class ScenarioParametersHttpService(
    config: Config,
    authenticator: AuthenticationResources,
    scenarioParametersService: ProcessingTypeDataProvider[_, ScenarioParametersService]
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(config, authenticator)
    with LazyLogging {

  private val parametersApiEndpoints = new ScenarioParametersEndpoints(authenticator.authenticationMethod())

  expose {
    parametersApiEndpoints.scenarioParametersCombinationsEndpoint
      .serverSecurityLogic(authorizeKnownUser[Unit])
      .serverLogicSuccess { implicit loggedUser: LoggedUser => _ =>
        Future {
          scenarioParametersService.combined.scenarioParametersCombinationsWithWritePermission
            .map(parametersWithEngineErrors =>
              Dtos.ScenarioParametersCombination(
                parametersWithEngineErrors.parameters.processingMode,
                parametersWithEngineErrors.parameters.category,
                EngineSetupDetails(
                  parametersWithEngineErrors.parameters.engineSetupName,
                  parametersWithEngineErrors.engineSetupErrors
                )
              )
            )
            .sortBy(combination =>
              (combination.processingMode.value, combination.category, combination.engineSetup.name.value)
            )
        }
      }
  }

}
