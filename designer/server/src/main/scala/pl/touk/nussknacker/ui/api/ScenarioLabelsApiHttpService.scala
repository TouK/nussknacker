package pl.touk.nussknacker.ui.api

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.api.description.ScenarioLabelsApiEndpoints
import pl.touk.nussknacker.ui.api.description.ScenarioLabelsApiEndpoints.Dtos._
import pl.touk.nussknacker.ui.process.label.ScenarioLabelsService
import pl.touk.nussknacker.ui.security.api.{AuthManager, LoggedUser}

import scala.concurrent.{ExecutionContext, Future}

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

  expose {
    labelsApiEndpoints.validateScenarioLabelsEndpoint
      .serverSecurityLogic(authorizeKnownUser[Unit])
      .serverLogicSuccess { implicit loggedUser: LoggedUser => request =>
        Future.successful {
          service
            .validatedScenarioLabels(request.labels)
            .swap
            .map { errors =>
              ScenarioLabelsValidationResponseDto(validationErrors =
                errors.map(e => ValidationError(label = e.label, messages = e.validationMessages.toList)).toList
              )
            }
            .getOrElse(ScenarioLabelsValidationResponseDto(validationErrors = List.empty))
        }
      }
  }

}
