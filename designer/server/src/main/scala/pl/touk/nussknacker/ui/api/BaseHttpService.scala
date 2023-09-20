package pl.touk.nussknacker.ui.api

import com.typesafe.config.Config
import pl.touk.nussknacker.ui.process.ProcessCategoryService
import pl.touk.nussknacker.ui.security.api._
import sttp.tapir.server.ServerEndpoint

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.{ExecutionContext, Future}

abstract class BaseHttpService(config: Config,
                               processCategoryService: ProcessCategoryService,
                               authenticator: AuthenticationResources)
                              (implicit executionContext: ExecutionContext) {

  private val allServerEndpoints = new AtomicReference(List.empty[ServerEndpoint[Any, Future]])

  protected def expose(serverEndpoint: ServerEndpoint[Any, Future]): Unit = {
    allServerEndpoints
      .accumulateAndGet(
        List(serverEndpoint),
        (l1, l2) => l1 ::: l2
      )
  }

  protected def expose(when: => Boolean)(serverEndpoint: ServerEndpoint[Any, Future]): Unit = {
    if (when) expose(serverEndpoint)
  }

  def serverEndpoints: List[ServerEndpoint[Any, Future]] = allServerEndpoints.get()

  protected def authorizeAdminUser[ERROR](credentials: AuthCredentials): Future[Either[Either[ERROR, SecurityError], LoggedUser]] = {
    authorizeCommonUser[ERROR](credentials)
      .map {
        case right@Right(AdminUser(_, _)) => right
        case Right(_: CommonUser) => Left(Right(SecurityError.AuthorizationError))
        case error@Left(_) => error
      }
  }

  protected def authorizeCommonUser[ERROR](credentials: AuthCredentials): Future[Either[Either[ERROR, SecurityError], LoggedUser]] = {
    authenticator
      .authenticate(credentials)
      .map {
        case Some(user) if user.roles.nonEmpty =>
          Right(LoggedUser(
            authenticatedUser = user,
            rules = AuthenticationConfiguration.getRules(config),
            processCategories = processCategoryService.getAllCategories
          ))
        case Some(_) =>
          Left(Right(SecurityError.AuthorizationError))
        case None =>
          Left(Right(SecurityError.AuthenticationError))
      }
  }

  protected def success[RESULT](value: RESULT) = Right(value)

  protected def businessError[ERROR](error: ERROR) = Left(Left(error))
}
