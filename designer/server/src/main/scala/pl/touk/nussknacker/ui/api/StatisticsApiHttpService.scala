package pl.touk.nussknacker.ui.api

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.api.description.StatisticsApiEndpoints
import pl.touk.nussknacker.ui.api.description.StatisticsApiEndpoints.Dtos.{
  CannotGenerateStatisticError,
  RegisterStatisticsRequestDto,
  StatisticError,
  StatisticUrlResponseDto
}
import pl.touk.nussknacker.ui.db.timeseries.{FEStatisticsRepository, WriteFEStatisticsRepository}
import pl.touk.nussknacker.ui.security.api.AuthManager
import pl.touk.nussknacker.ui.statistics.{RawFEStatistics, StatisticUrlConfig, UsageStatisticsReportsSettingsService}

import scala.concurrent.{ExecutionContext, Future}

class StatisticsApiHttpService(
    authManager: AuthManager,
    usageStatisticsReportsSettingsService: UsageStatisticsReportsSettingsService,
    repository: FEStatisticsRepository[Future],
    urlConfig: StatisticUrlConfig,
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
          .map(_.flatMap(_.prepareURLs(urlConfig)))
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
    ignoringErrorsRepository.write(RawFEStatistics(request))
  }

  private class IgnoringErrorsStatisticsRepository(repository: FEStatisticsRepository[Future])
      extends WriteFEStatisticsRepository[Future] {

    override def write(rawFEStatistics: RawFEStatistics): Future[Unit] = repository
      .write(rawFEStatistics)
      .recover { case ex: Exception =>
        logger.warn("Exception occurred during statistics write", ex)
        ()
      }

  }

}
