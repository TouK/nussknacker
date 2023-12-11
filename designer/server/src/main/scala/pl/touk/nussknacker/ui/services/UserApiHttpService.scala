package pl.touk.nussknacker.ui.services

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.api.{DisplayableUser, UserApiEndpoints}
import pl.touk.nussknacker.ui.process.{ProcessCategoryService, UserCategoryService}
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, LoggedUser}

import scala.concurrent.{ExecutionContext, Future}

class UserApiHttpService(
    config: Config,
    authenticator: AuthenticationResources,
    getProcessCategoryService: () => ProcessCategoryService,
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(config, getProcessCategoryService, authenticator)
    with LazyLogging {

  private val userApiEndpoints = new UserApiEndpoints(authenticator.authenticationMethod())

  expose {
    userApiEndpoints.userInfoEndpoint
      .serverSecurityLogic(authorizeKnownUser[Unit])
      .serverLogic { user: LoggedUser => _ =>
        Future(
          success(
            DisplayableUser(user, new UserCategoryService(getProcessCategoryService()).getUserCategories(user))
          )
        )
      }
  }

}
