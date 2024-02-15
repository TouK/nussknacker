package pl.touk.nussknacker.ui.services

import cats.data.EitherT
import com.typesafe.config.Config
import pl.touk.nussknacker.restmodel.SecurityError
import pl.touk.nussknacker.restmodel.SecurityError.{AuthenticationError, AuthorizationError}
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.security.api._
import pl.touk.nussknacker.ui.services.BaseHttpService.{CustomAuthorizationError, NoRequirementServerEndpoint}
import sttp.tapir.server.{PartialServerEndpoint, ServerEndpoint}

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.{ExecutionContext, Future}

abstract class BaseHttpService(
    config: Config,
    authenticator: AuthenticationResources
)(implicit executionContext: ExecutionContext) {

  // the discussion about this approach can be found here: https://github.com/TouK/nussknacker/pull/4685#discussion_r1329794444
  type LogicResult[BUSINESS_ERROR, RESULT] = Either[Either[BUSINESS_ERROR, SecurityError], RESULT]

  private val allServerEndpoints = new AtomicReference(List.empty[NoRequirementServerEndpoint])
  private val authConfigRules    = AuthenticationConfiguration.getRules(config)

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
          success(LoggedUser(user, authConfigRules))
        case Some(_) =>
          securityError(AuthorizationError)
        case None =>
          securityError(AuthenticationError)
      }
  }

  protected def success[RESULT](value: RESULT) = Right(value)

  protected def businessError[BUSINESS_ERROR](error: BUSINESS_ERROR) = Left(Left(error))

  protected def securityError[SE <: SecurityError](error: SE) = Left(Right(error))

  private type PartialEndpoint[INPUT, OUTPUT, BUSINESS_ERROR, -R] =
    PartialServerEndpoint[_, LoggedUser, INPUT, Either[BUSINESS_ERROR, SecurityError], OUTPUT, R, Future]

  implicit class ServerLogicExtension[INPUT, OUTPUT, BUSINESS_ERROR, -R](
      endpoint: PartialEndpoint[INPUT, OUTPUT, BUSINESS_ERROR, R]
  ) {

    def serverLogicEitherT(f: LoggedUser => INPUT => EitherT[Future, BUSINESS_ERROR, OUTPUT]) =
      endpoint.serverLogic { loggedUser: LoggedUser => input: INPUT =>
        f(loggedUser)(input).value.map(toTapirResponse)
      }

    private def toTapirResponse(result: Either[BUSINESS_ERROR, OUTPUT]): LogicResult[BUSINESS_ERROR, OUTPUT] =
      result match {
        case Left(error) =>
          error match {
            case _: CustomAuthorizationError => securityError(AuthorizationError)
            case e                           => businessError(e)
          }
        case Right(value) => success(value)
      }

  }

}

object BaseHttpService {

  // we assume that our endpoints have no special requirements (in the Tapir sense)
  type NoRequirementServerEndpoint = ServerEndpoint[Any, Future]

  // it's marker interface which simplifies error handling when serverLogicEitherT is used
  trait CustomAuthorizationError
}
