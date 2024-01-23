package pl.touk.nussknacker.ui.services

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.component.ComponentId
import pl.touk.nussknacker.restmodel.component.ComponentApiEndpoints
import pl.touk.nussknacker.ui.definition.component.ComponentService
import pl.touk.nussknacker.ui.process.ProcessCategoryService
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, LoggedUser}

import scala.concurrent.ExecutionContext

class ComponentApiHttpService(
    config: Config,
    authenticator: AuthenticationResources,
    getProcessCategoryService: () => ProcessCategoryService,
    componentService: ComponentService
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(config, getProcessCategoryService, authenticator)
    with LazyLogging {

  private val componentApiEndpoints = new ComponentApiEndpoints(authenticator.authenticationMethod())

  expose {
    componentApiEndpoints.componentsListEndpoint
      .serverSecurityLogic(authorizeKnownUser[Unit])
      .serverLogic { user => _ =>
        componentService
          .getComponentsList(user)
          .map { componentList => success(componentList) }
      }
  }

  expose {
    componentApiEndpoints.componentUsageEndpoint
      .serverSecurityLogic(authorizeKnownUser[String])
      .serverLogic { user: LoggedUser => componentId: ComponentId =>
        componentService
          .getComponentUsages(componentId)(user)
          .map {
            case Left(_)      => businessError(s"Component $componentId not exist.")
            case Right(value) => success(value)
          }
      }
  }

}
