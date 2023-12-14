package pl.touk.nussknacker.ui.services

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.api.NotificationApiEndpoints
import pl.touk.nussknacker.ui.notifications.NotificationService
import pl.touk.nussknacker.ui.process.ProcessCategoryService
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, LoggedUser}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class NotificationApiHttpService(
    config: Config,
    authenticator: AuthenticationResources,
    getProcessCategoryService: () => ProcessCategoryService,
    notificationService: NotificationService
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(config, getProcessCategoryService, authenticator)
    with LazyLogging {

  private val notificationApiEndpoints = new NotificationApiEndpoints(authenticator.authenticationMethod())

  expose {
    val baseErrorMessage = "The query parameter 'after' was malformed:\n" +
      "DecodingFailure at : "
    notificationApiEndpoints.notificationEndpoint
      .serverSecurityLogic(authorizeKnownUser[String])
      .serverLogic { user: LoggedUser => after: Option[String] =>
        after match {
          case Some(value) =>
            parseTime(value) match {
              case Left(error) =>
                Future(businessError(baseErrorMessage + error))
              case Right(time) =>
                notificationService
                  .notifications(Some(time))(user, executionContext)
                  .map { notificationList => success(notificationList) }
            }

          case None =>
            notificationService
              .notifications(None)(user, executionContext)
              .map { notificationList => success(notificationList) }
        }
      }
  }

  private def parseTime(instantInQuotes: String): Either[String, Instant] = {
    val cleanInstant = instantInQuotes.stripPrefix("\"").stripSuffix("\"")

    if (instantInQuotes.length == cleanInstant.length)
      return Left(s"Got value '$instantInQuotes' with wrong type, expecting string")

    Try(Instant.parse(cleanInstant)) match {
      case Failure(exception) => Left(exception.getMessage)
      case Success(value)     => Right(value)
    }
  }

}
