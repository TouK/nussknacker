package pl.touk.nussknacker.ui.services

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.component.DesignerWideComponentId
import pl.touk.nussknacker.restmodel.component.ComponentApiEndpoints
import pl.touk.nussknacker.ui.definition.component.ComponentService
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, LoggedUser}

import scala.concurrent.ExecutionContext

class ComponentApiHttpService(
    authenticator: AuthenticationResources,
    componentService: ComponentService
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(authenticator)
    with LazyLogging {

  private val componentApiEndpoints = new ComponentApiEndpoints(authenticator.authenticationMethod())

  expose {
    componentApiEndpoints.componentsListEndpoint
      .serverSecurityLogic(authorizeKnownUser[Unit])
      .serverLogic { implicit loggedUser => _ =>
        componentService.getComponentsList
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
