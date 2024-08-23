package pl.touk.nussknacker.ui.api

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.api.description.{DisplayableUser, UserApiEndpoints}
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.{AuthManager, LoggedUser}

import scala.concurrent.{ExecutionContext, Future}

class UserApiHttpService(
    authManager: AuthManager,
    categories: ProcessingTypeDataProvider[String, _],
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(authManager)
    with LazyLogging {

  private val userApiEndpoints = new UserApiEndpoints(authManager.authenticationEndpointInput())

  expose {
    userApiEndpoints.userInfoEndpoint
      .serverSecurityLogic(authorizeKnownUser[Unit])
      .serverLogic { user: LoggedUser => _ =>
        Future(
          success(
            DisplayableUser(user, categories.all(user).values.toList.distinct.sorted)
          )
        )
      }
  }

}
