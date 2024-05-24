package pl.touk.nussknacker.ui.api

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.api.description.{DisplayableUser, UserApiEndpoints}
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.{AuthenticationManager, LoggedUser}

import scala.concurrent.{ExecutionContext, Future}

class UserApiHttpService(
    authenticationManager: AuthenticationManager,
    categories: ProcessingTypeDataProvider[String, _],
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(authenticationManager)
    with LazyLogging {

  private val userApiEndpoints = new UserApiEndpoints(authenticationManager.authenticationEndpointInput())

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
