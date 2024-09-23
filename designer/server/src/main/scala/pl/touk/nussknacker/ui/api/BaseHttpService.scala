package pl.touk.nussknacker.ui.api

import cats.data.EitherT
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.api.BaseHttpService.{CustomAuthorizationError, NoRequirementServerEndpoint}
import pl.touk.nussknacker.ui.security.api.SecurityError.InsufficientPermission
import pl.touk.nussknacker.ui.security.api._
import sttp.tapir.server.{PartialServerEndpoint, ServerEndpoint}

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.{ExecutionContext, Future}

abstract class BaseHttpService(
    authManager: AuthManager
)(implicit executionContext: ExecutionContext) {

  // the discussion about this approach can be found here: https://github.com/TouK/nussknacker/pull/4685#discussion_r1329794444
  type LogicResult[BUSINESS_ERROR, RESULT] = Either[Either[BUSINESS_ERROR, SecurityError], RESULT]

  private val allServerEndpoints = new AtomicReference(List.empty[NoRequirementServerEndpoint])

  protected def expose(serverEndpoint: NoRequirementServerEndpoint): Unit = {
    expose(List(serverEndpoint))
  }

  protected def expose(serverEndpoints: List[NoRequirementServerEndpoint]): Unit = {
    allServerEndpoints
      .accumulateAndGet(
        serverEndpoints,
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
        case Right(_: CommonUser)           => securityError(InsufficientPermission)
        case Right(_: ImpersonatedUser)     => securityError(InsufficientPermission)
        case error @ Left(_)                => error
      }
  }

  protected def authorizeKnownUser[BUSINESS_ERROR](
      credentials: AuthCredentials
  ): Future[LogicResult[BUSINESS_ERROR, LoggedUser]] = {
    authManager
      .authenticate(credentials)
      .map {
        case Left(authenticationError) => securityError(authenticationError)
        case Right(authenticatedUser) =>
          authManager.authorize(authenticatedUser) match {
            case Right(loggedUser)        => success(loggedUser)
            case Left(authorizationError) => securityError(authorizationError)
          }
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

    def serverLogicFlatErrors(f: LoggedUser => INPUT => Future[Either[BUSINESS_ERROR, OUTPUT]]) =
      endpoint.serverLogic { loggedUser: LoggedUser => input: INPUT =>
        f(loggedUser)(input).map(toTapirResponse)
      }

    // We have EitherT variant because EitherT hasn't got covariant A and E parameters and when we when is used with serverLogicFlatErrors,
    // we have to declare these types explicitly
    def serverLogicEitherT(f: LoggedUser => INPUT => EitherT[Future, BUSINESS_ERROR, OUTPUT]) =
      endpoint.serverLogic { loggedUser: LoggedUser => input: INPUT =>
        f(loggedUser)(input).value.map(toTapirResponse)
      }

    private def toTapirResponse(result: Either[BUSINESS_ERROR, OUTPUT]): LogicResult[BUSINESS_ERROR, OUTPUT] =
      result match {
        case Left(error) =>
          error match {
            case _: CustomAuthorizationError => securityError(InsufficientPermission)
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
