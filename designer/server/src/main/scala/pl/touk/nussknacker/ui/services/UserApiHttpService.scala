package pl.touk.nussknacker.ui.services

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.api.{DisplayableUser, UserApiEndpoints}
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, LoggedUser}

import scala.concurrent.{ExecutionContext, Future}

class UserApiHttpService(
    config: Config,
    authenticator: AuthenticationResources,
    categories: ProcessingTypeDataProvider[String, _],
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(config, authenticator)
    with LazyLogging {

  private val userApiEndpoints = new UserApiEndpoints(authenticator.authenticationMethod())

  expose {
    userApiEndpoints.userInfoEndpoint
      .serverSecurityLogic(authorizeKnownUser[Unit])
      .serverLogic { user: LoggedUser => _ =>
        Future(
          success(
            DisplayableUser(user, categories.all(user).values)
          )
        )
      }
  }

}
