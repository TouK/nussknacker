package pl.touk.nussknacker.ui.api

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.api.description.ScenarioLabelsApiEndpoints
import pl.touk.nussknacker.ui.api.description.ScenarioLabelsApiEndpoints.Dtos.ScenarioLabels
import pl.touk.nussknacker.ui.process.ScenarioLabelsService
import pl.touk.nussknacker.ui.security.api.{AuthManager, LoggedUser}

import scala.concurrent.ExecutionContext

class ScenarioLabelsApiHttpService(
    authManager: AuthManager,
    service: ScenarioLabelsService
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(authManager)
    with LazyLogging {

  private val labelsApiEndpoints = new ScenarioLabelsApiEndpoints(
    authManager.authenticationEndpointInput()
  )

  expose {
    labelsApiEndpoints.scenarioLabelsEndpoint
      .serverSecurityLogic(authorizeKnownUser[Unit])
      .serverLogicSuccess { implicit loggedUser: LoggedUser => _ =>
        service
          .readLabels()
          .map(labels => ScenarioLabels(labels))
      }
  }

}
