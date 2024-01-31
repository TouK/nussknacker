package pl.touk.nussknacker.ui.services

import com.typesafe.config.Config
import pl.touk.nussknacker.restmodel.SecurityError.{AuthenticationError, AuthorizationError}
import pl.touk.nussknacker.restmodel.{BusinessError, NuException, SecurityError}
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.security.api._
import pl.touk.nussknacker.ui.services.BaseHttpService.NoRequirementServerEndpoint
import sttp.tapir.server.ServerEndpoint

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.{ExecutionContext, Future}

abstract class BaseHttpService(
    config: Config,
    authenticator: AuthenticationResources
)(implicit executionContext: ExecutionContext) {

  // the discussion about this approach can be found here: https://github.com/TouK/nussknacker/pull/4685#discussion_r1329794444
  type LogicResult[BUSINESS_ERROR, RESULT] = Either[Either[BUSINESS_ERROR, SecurityError], RESULT]

  private val allServerEndpoints = new AtomicReference(List.empty[NoRequirementServerEndpoint])

  protected def expose(serverEndpoint: NoRequirementServerEndpoint): Unit = {
    allServerEndpoints
      .accumulateAndGet(
        List(serverEndpoint),
        (l1, l2) => l1 ::: l2
      )
  }

  protected def expose(when: => Boolean)(serverEndpoint: NoRequirementServerEndpoint): Unit = {
    if (when) expose(serverEndpoint)
  }

  def serverEndpoints: List[NoRequirementServerEndpoint] = allServerEndpoints.get()

  protected def authorizeAdminUser[BUSINESS_ERROR](
      credentials: AuthCredentials
  ): Future[LogicResult[BUSINESS_ERROR, LoggedUser]] = {
    authorizeKnownUser[BUSINESS_ERROR](credentials)
      .map {
        case right @ Right(AdminUser(_, _)) => right
        case Right(_: CommonUser)           => securityError(AuthorizationError)
        case error @ Left(_)                => error
      }
  }

  protected def authorizeKnownUser[BUSINESS_ERROR](
      credentials: AuthCredentials
  ): Future[LogicResult[BUSINESS_ERROR, LoggedUser]] = {
    authenticator
      .authenticate(credentials)
      .map {
        case Some(user) if user.roles.nonEmpty =>
          success(
            LoggedUser(
              authenticatedUser = user,
              rules = AuthenticationConfiguration.getRules(config)
            )
          )
        case Some(_) =>
          securityError(AuthorizationError)
        case None =>
          securityError(AuthenticationError)
      }
  }

  protected def success[RESULT](value: RESULT) = Right(value)

  protected def businessError[BUSINESS_ERROR](error: BUSINESS_ERROR) = Left(Left(error))

  protected def securityError[SE <: SecurityError](error: SE) = Left(Right(error))

  implicit class LogicResultExtension[RESULT](result: Future[RESULT]) {

    def toTapirResponse(): Future[LogicResult[BusinessError, RESULT]] = result
      .map(success)
      .recover { case NuException(nuError) =>
        nuError match {
          case error: SecurityError => securityError(error)
          case error: BusinessError => businessError(error)
        }
      }

  }

}

object BaseHttpService {

  // we assume that our endpoints have no special requirements (in the Tapir sense)
  type NoRequirementServerEndpoint = ServerEndpoint[Any, Future]
}
