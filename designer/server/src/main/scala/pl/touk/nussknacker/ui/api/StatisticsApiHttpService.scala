package pl.touk.nussknacker.ui.api

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.api.description.StatisticsApiEndpoints
import pl.touk.nussknacker.ui.api.description.StatisticsApiEndpoints.Dtos.{
  CannotGenerateStatisticError,
  RegisterStatisticsRequestDto,
  StatisticError,
  StatisticUrlResponseDto
}
import pl.touk.nussknacker.ui.db.timeseries.FEStatisticsRepository
import pl.touk.nussknacker.ui.security.api.AuthenticationResources
import pl.touk.nussknacker.ui.statistics.UsageStatisticsReportsSettingsService

import scala.concurrent.{ExecutionContext, Future}

class StatisticsApiHttpService(
    authenticator: AuthenticationResources,
    usageStatisticsReportsSettingsService: UsageStatisticsReportsSettingsService,
    repository: FEStatisticsRepository[Future]
)(implicit ec: ExecutionContext)
    extends BaseHttpService(authenticator)
    with LazyLogging {

  private val endpoints = new StatisticsApiEndpoints(authenticator.authenticationMethod())

  expose {
    endpoints.statisticUsageEndpoint
      .serverSecurityLogic(authorizeKnownUser[StatisticError])
      .serverLogic { _ => _ =>
        usageStatisticsReportsSettingsService
          .prepareStatisticsUrl()
          .map {
            case Left(_)         => businessError(CannotGenerateStatisticError)
            case Right(maybeUrl) => success(StatisticUrlResponseDto(maybeUrl.toList))
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
      .groupBy(_.name.entryName)
      .map { case (k, v) =>
        k -> v.size.toLong
      }
    repository.write(groupedByName)
  }

}
