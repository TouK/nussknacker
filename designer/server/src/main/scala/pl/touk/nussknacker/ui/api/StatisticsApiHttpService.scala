package pl.touk.nussknacker.ui.api

import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.api.StatisticsApiHttpService.toURL
import pl.touk.nussknacker.ui.api.description.StatisticsApiEndpoints
import pl.touk.nussknacker.ui.api.description.StatisticsApiEndpoints.Dtos.{
  CannotGenerateStatisticError,
  RegisterStatisticsRequestDto,
  StatisticError,
  StatisticUrlResponseDto
}
import pl.touk.nussknacker.ui.db.timeseries.{FEStatisticsRepository, WriteFEStatisticsRepository}
import pl.touk.nussknacker.ui.security.api.AuthManager
import pl.touk.nussknacker.ui.statistics.UsageStatisticsReportsSettingsService

import java.net.{URI, URL}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class StatisticsApiHttpService(
    authManager: AuthManager,
    usageStatisticsReportsSettingsService: UsageStatisticsReportsSettingsService,
    repository: FEStatisticsRepository[Future]
)(implicit ec: ExecutionContext)
    extends BaseHttpService(authManager)
    with LazyLogging {

  private val endpoints                = new StatisticsApiEndpoints(authManager.authenticationEndpointInput())
  private val ignoringErrorsRepository = new IgnoringErrorsStatisticsRepository(repository)

  expose {
    endpoints.statisticUsageEndpoint
      .serverSecurityLogic(authorizeKnownUser[StatisticError])
      .serverLogic { _ => _ =>
        usageStatisticsReportsSettingsService
          .prepareStatisticsUrl()
          .map(_.flatMap(_.map(toURL).sequence))
          .map {
            case Left(_)     => businessError(CannotGenerateStatisticError)
            case Right(urls) => success(StatisticUrlResponseDto(urls))
          }
      }
  }

  expose {
    endpoints.registerStatisticsEndpoint
      .serverSecurityLogic(authorizeKnownUser[Unit])
      .serverLogicSuccess { _ => request =>
        saveStatistics(request)
        Future.successful(())
      }
  }

  private def saveStatistics(request: RegisterStatisticsRequestDto): Future[Unit] = {
    // todo change to groupMapReduce in scala 2.13
    val groupedByName = request.statistics
      .groupBy(_.name.shortName)
      .map { case (k, v) =>
        k -> v.size.toLong
      }
    ignoringErrorsRepository.write(groupedByName)
  }

  private class IgnoringErrorsStatisticsRepository(repository: FEStatisticsRepository[Future])
      extends WriteFEStatisticsRepository[Future] {

    override def write(statistics: Map[String, Long]): Future[Unit] = repository
      .write(statistics)
      .recover { case ex: Exception =>
        logger.warn("Exception occurred during statistics write", ex)
        ()
      }

  }

}

object StatisticsApiHttpService extends LazyLogging {

  private[api] def toURL(urlString: String): Either[StatisticError, URL] =
    Try(new URI(urlString).toURL) match {
      case Failure(ex) => {
        logger.warn(s"Exception occurred while creating URL from string: [$urlString]", ex)
        Left(CannotGenerateStatisticError)
      }
      case Success(value) => Right(value)
    }

}
