package pl.touk.nussknacker.ui.api

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.component.DesignerWideComponentId
import pl.touk.nussknacker.restmodel.component.ComponentApiEndpoints
import pl.touk.nussknacker.ui.definition.component.ComponentService
import pl.touk.nussknacker.ui.security.api.{AuthManager, LoggedUser}

import scala.concurrent.ExecutionContext

class ComponentApiHttpService(
    authManager: AuthManager,
    componentService: ComponentService
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(authManager)
    with LazyLogging {

  private val componentApiEndpoints = new ComponentApiEndpoints(authManager.authenticationEndpointInput())

  expose {
    componentApiEndpoints.componentsListEndpoint
      .serverSecurityLogic(authorizeKnownUser[Unit])
      .serverLogic { implicit loggedUser => skipUsages =>
        componentService
          .getComponentsList(skipUsages.getOrElse(false))
          .map { componentList => success(componentList) }
      }
  }

  expose {
    componentApiEndpoints.componentUsageEndpoint
      .serverSecurityLogic(authorizeKnownUser[String])
      .serverLogic { implicit loggedUser: LoggedUser => designerWideComponentId: DesignerWideComponentId =>
        componentService
          .getComponentUsages(designerWideComponentId)
          .map {
            case Left(_)      => businessError(s"Component $designerWideComponentId not exist.")
            case Right(value) => success(value)
          }
      }
  }

}
